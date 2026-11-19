package io.github.leonardodvf.deliverytracker.shipment.repository;

import io.github.leonardodvf.deliverytracker.shipment.entity.Shipment;
import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    boolean existsByQuoteId(UUID quoteId);

    Optional<Shipment> findByIdAndCustomerId(UUID id, UUID customerId);

    boolean existsByIdAndCustomerId(UUID id, UUID customerId);

    Page<Shipment> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Shipment> findByCustomerIdAndStatus(UUID customerId, ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);
}
