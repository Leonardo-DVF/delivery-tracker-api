package io.github.leonardodvf.deliverytracker.freight.service;

import io.github.leonardodvf.deliverytracker.freight.exception.ConflictingFreightRatesException;
import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class FreightService {

    private final FreightRateRepository rates;

    @Transactional(readOnly = true)
    public BigDecimal calculateAmount(String destinationPostalCode, BigDecimal weightKg) {
        var matches = rates.findMatchingRates(Integer.parseInt(destinationPostalCode), weightKg);
        if (matches.isEmpty()) {
            throw new FreightRateUnavailableException();
        }
        if (matches.size() > 1) {
            throw new ConflictingFreightRatesException();
        }
        var rate = matches.getFirst();
        return rate.getBasePrice().add(rate.getPricePerKg().multiply(weightKg)).setScale(2, RoundingMode.HALF_UP);
    }
}
