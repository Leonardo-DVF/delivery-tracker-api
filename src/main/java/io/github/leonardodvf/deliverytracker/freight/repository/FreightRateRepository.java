package io.github.leonardodvf.deliverytracker.freight.repository;

import io.github.leonardodvf.deliverytracker.freight.entity.FreightRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface FreightRateRepository extends JpaRepository<FreightRate, UUID> {

    @Query("""
            SELECT rate FROM FreightRate rate
            WHERE :postalCode BETWEEN rate.postalStart AND rate.postalEnd
              AND :weightKg > rate.weightMin AND :weightKg <= rate.weightMax
            """)
    List<FreightRate> findMatchingRates(int postalCode, BigDecimal weightKg);
}
