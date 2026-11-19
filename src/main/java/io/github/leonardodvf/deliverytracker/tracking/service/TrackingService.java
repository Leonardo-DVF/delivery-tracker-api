package io.github.leonardodvf.deliverytracker.tracking.service;

import io.github.leonardodvf.deliverytracker.shipment.exception.ShipmentNotFoundException;
import io.github.leonardodvf.deliverytracker.shipment.repository.ShipmentRepository;
import io.github.leonardodvf.deliverytracker.tracking.dto.RecordTrackingEventRequest;
import io.github.leonardodvf.deliverytracker.tracking.dto.TrackingEventResponse;
import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import io.github.leonardodvf.deliverytracker.tracking.repository.TrackingEventRepository;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
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
        var user = users.findById(operatorId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
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
}
