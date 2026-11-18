package io.github.leonardodvf.deliverytracker.shipment.service;

import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.shared.dto.PageResponse;
import io.github.leonardodvf.deliverytracker.shipment.dto.CreateShipmentRequest;
import io.github.leonardodvf.deliverytracker.shipment.dto.ShipmentResponse;
import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import io.github.leonardodvf.deliverytracker.shipment.exception.DestinationMismatchException;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteAlreadyUsedException;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteExpiredException;
import io.github.leonardodvf.deliverytracker.shipment.exception.FreightQuoteNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    /** Matches the (customer_id|status, created_at DESC, id) indexes created in V4. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final UserRepository users;
    private final FreightQuoteRepository quotes;
    private final ShipmentRepository shipments;
    private final TrackingEventRepository events;
    private final Clock clock;

    @Transactional
    public ShipmentResponse create(UUID customerId, CreateShipmentRequest request) {
        var customer = authenticatedUser(customerId);
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customers can create shipments");
        }
        // A quote owned by someone else is reported as missing so its existence is not revealed.
        var quote = quotes.findById(request.quoteId())
                .filter(found -> found.getRequester().getId().equals(customerId))
                .orElseThrow(FreightQuoteNotFoundException::new);
        var now = clock.instant();
        if (quote.isExpiredAt(now)) {
            throw new FreightQuoteExpiredException();
        }
        if (!quote.getDestinationPostalCode().equals(request.destination().postalCode())) {
            throw new DestinationMismatchException();
        }
        // Fast path for a friendly error; the unique constraint on quote_id still guards concurrent requests.
        if (shipments.existsByQuoteId(quote.getId())) {
            throw new FreightQuoteAlreadyUsedException();
        }
        var shipment = new Shipment(customer, quote, request.origin().toAddress(), request.destination().toAddress(),
                request.lengthCm(), request.widthCm(), request.heightCm(), now);
        shipments.saveAndFlush(shipment);
        events.save(TrackingEvent.initial(shipment));
        return ShipmentResponse.from(shipment);
    }

    /** Customers only see their own shipments; operators see all of them. */
    @Transactional(readOnly = true)
    public PageResponse<ShipmentResponse> list(UUID userId, ShipmentStatus status, int page, int size) {
        var user = authenticatedUser(userId);
        var pageable = PageRequest.of(page, size, NEWEST_FIRST);
        var result = switch (user.getRole()) {
            case CUSTOMER -> status == null
                    ? shipments.findByCustomerId(userId, pageable)
                    : shipments.findByCustomerIdAndStatus(userId, status, pageable);
            case OPERATOR -> status == null
                    ? shipments.findAll(pageable)
                    : shipments.findByStatus(status, pageable);
        };
        return PageResponse.from(result, ShipmentResponse::from);
    }

    /** Another customer's shipment is reported as missing so its existence is not revealed. */
    @Transactional(readOnly = true)
    public ShipmentResponse get(UUID userId, UUID shipmentId) {
        var user = authenticatedUser(userId);
        var shipment = switch (user.getRole()) {
            case CUSTOMER -> shipments.findByIdAndCustomerId(shipmentId, userId);
            case OPERATOR -> shipments.findById(shipmentId);
        };
        return shipment.map(ShipmentResponse::from).orElseThrow(ShipmentNotFoundException::new);
    }

    private User authenticatedUser(UUID userId) {
        return users.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }
}
