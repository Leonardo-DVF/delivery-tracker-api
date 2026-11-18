package io.github.leonardodvf.deliverytracker.freight.service;

import io.github.leonardodvf.deliverytracker.freight.config.FreightProperties;
import io.github.leonardodvf.deliverytracker.freight.entity.FreightQuote;
import io.github.leonardodvf.deliverytracker.freight.exception.ConflictingFreightRatesException;
import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightRateRepository;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FreightService {

    private final FreightRateRepository rates;
    private final FreightQuoteRepository quotes;
    private final UserRepository users;
    private final FreightProperties properties;
    private final Clock clock;

    @Transactional
    public FreightQuote createQuote(UUID requesterId, String destinationPostalCode, BigDecimal weightKg) {
        var requester = users.findById(requesterId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        var amount = calculateAmount(destinationPostalCode, weightKg);
        return quotes.save(new FreightQuote(requester, destinationPostalCode, weightKg, amount,
                clock.instant(), properties.quoteTtl()));
    }

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
