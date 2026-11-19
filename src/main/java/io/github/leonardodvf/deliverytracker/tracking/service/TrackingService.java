package io.github.leonardodvf.deliverytracker.tracking.service;

import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.dto.PublicTrackingResponse;
import io.github.leonardodvf.deliverytracker.tracking.dto.RecordTrackingEventRequest;
import io.github.leonardodvf.deliverytracker.tracking.dto.TrackingEventResponse;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final UserRepository users;
    private final ShipmentRepository shipments;
    private final TrackingEventRepository events;
    private final Clock clock;

    /**
     * Moves the shipment to the requested status and appends the matching event in one transaction.
     * The shipment's @Version makes a concurrent change fail with an optimistic locking conflict.
     */
    @Transactional
    public TrackingEventResponse record(UUID operatorId, UUID shipmentId, RecordTrackingEventRequest request) {
        var user = authenticatedUser(operatorId);
        if (user.getRole() != UserRole.OPERATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only operators can record tracking events");
        }
        var shipment = shipments.findById(shipmentId).orElseThrow(ShipmentNotFoundException::new);
        shipment.transitionTo(request.status(), clock.instant());
        // Flush now so a version conflict surfaces here, before the event is written.
        shipments.saveAndFlush(shipment);
        var event = events.save(TrackingEvent.statusChange(shipment, TrackingEvent.SOURCE_OPERATOR, request.location()));
        return TrackingEventResponse.from(event);
    }

    /** Oldest first. Another customer's shipment is reported as missing so its existence is not revealed. */
    @Transactional(readOnly = true)
    public List<TrackingEventResponse> history(UUID userId, UUID shipmentId) {
        var user = authenticatedUser(userId);
        var visible = switch (user.getRole()) {
            case CUSTOMER -> shipments.existsByIdAndCustomerId(shipmentId, userId);
            case OPERATOR -> shipments.existsById(shipmentId);
        };
        if (!visible) {
            throw new ShipmentNotFoundException();
        }
        return events.findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(shipmentId).stream()
                .map(TrackingEventResponse::from).toList();
    }

    /** Unauthenticated lookup; the tracking code has 122 random bits is the only key, so guessing codes is impractical. */
    @Transactional(readOnly = true)
    public PublicTrackingResponse publicTracking(String trackingCode) {
        var shipment = shipments.findByTrackingCode(trackingCode.toUpperCase(Locale.ROOT))
                .orElseThrow(ShipmentNotFoundException::new);
        return PublicTrackingResponse.from(shipment,
                events.findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(shipment.getId()));
    }

    private User authenticatedUser(UUID userId) {
        return users.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }
}
