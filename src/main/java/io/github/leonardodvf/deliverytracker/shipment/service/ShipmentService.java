package io.github.leonardodvf.deliverytracker.shipment.service;

import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import io.github.leonardodvf.deliverytracker.shipment.dto.CreateShipmentRequest;
import io.github.leonardodvf.deliverytracker.shipment.dto.ShipmentResponse;
import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final UserRepository users;
    private final FreightService freight;
    private final ShipmentRepository shipments;
    private final TrackingEventRepository events;

    @Transactional
    public ShipmentResponse create(UUID customerId, CreateShipmentRequest request) {
        var customer = users.findById(customerId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customers can create shipments");
        }
        var amount = freight.calculateAmount(request.destination().postalCode(), request.weightKg());
        var shipment = new Shipment(customer, request.origin().toAddress(), request.destination().toAddress(),
                request.weightKg(), request.lengthCm(), request.widthCm(), request.heightCm(), amount);
        shipments.saveAndFlush(shipment);
        events.save(TrackingEvent.initial(shipment));
        return ShipmentResponse.from(shipment);
    }
}
