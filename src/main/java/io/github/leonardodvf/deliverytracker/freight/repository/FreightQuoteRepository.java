package io.github.leonardodvf.deliverytracker.freight.repository;

import io.github.leonardodvf.deliverytracker.freight.entity.FreightQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FreightQuoteRepository extends JpaRepository<FreightQuote, UUID> {
}
