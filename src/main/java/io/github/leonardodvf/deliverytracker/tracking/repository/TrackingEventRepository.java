package io.github.leonardodvf.deliverytracker.tracking.repository;

import io.github.leonardodvf.deliverytracker.tracking.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {
    List<TrackingEvent> findByShipmentIdOrderByOccurredAtAscReceivedAtAsc(UUID shipmentId);

    boolean existsByCarrierEventId(String carrierEventId);
}
