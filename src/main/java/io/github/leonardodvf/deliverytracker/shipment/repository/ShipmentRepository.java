package io.github.leonardodvf.deliverytracker.shipment.repository;

import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    boolean existsByQuoteId(UUID quoteId);
}
