package io.github.leonardodvf.deliverytracker.freight.service;

import io.github.leonardodvf.deliverytracker.freight.config.FreightProperties;
import io.github.leonardodvf.deliverytracker.freight.entity.FreightQuote;
import io.github.leonardodvf.deliverytracker.freight.entity.FreightRate;
import io.github.leonardodvf.deliverytracker.freight.exception.ConflictingFreightRatesException;
import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightQuoteRepository;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightRateRepository;
import io.github.leonardodvf.deliverytracker.user.entity.User;
import io.github.leonardodvf.deliverytracker.user.entity.UserRole;
import io.github.leonardodvf.deliverytracker.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FreightServiceTests {

    private final FreightRateRepository rates = mock(FreightRateRepository.class);
    private final FreightQuoteRepository quotes = mock(FreightQuoteRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Instant now = Instant.parse("2026-11-18T12:00:00Z");
    private final FreightService freight = new FreightService(rates, quotes, users,
            new FreightProperties(Duration.ofMinutes(30)), Clock.fixed(now, ZoneOffset.UTC));

    @ParameterizedTest
    @CsvSource({
            "10.00, 2.50, 0.001, 10.00",
            "10.00, 2.50, 0.002, 10.01",
            "10.00, 2.50, 2.000, 15.00",
            "10.00, 2.50, 5.000, 22.50",
            "20.00, 3.00, 5.001, 35.00",
            "30.00, 5.00, 30.000, 180.00",
            "0.00, 0.00, 1.000, 0.00"
    })
    void calculatesWithExactDecimalArithmeticAndRoundsOnlyTheFinalAmount(String base, String perKg,
                                                                       String weight, String expected) {
        var kilograms = new BigDecimal(weight);
        when(rates.findMatchingRates(1000000, kilograms)).thenReturn(List.of(rate(base, perKg)));

        var amount = freight.calculateAmount("01000000", kilograms);

        assertThat(amount).isEqualByComparingTo(expected);
        assertThat(amount.scale()).isEqualTo(2);
        verify(rates).findMatchingRates(1000000, kilograms);
    }

    @Test
    void rejectsMissingTariffWithoutInventingAPrice() {
        var weight = new BigDecimal("31");
        when(rates.findMatchingRates(1, weight)).thenReturn(List.of());

        assertThatThrownBy(() -> freight.calculateAmount("00000001", weight))
                .isInstanceOf(FreightRateUnavailableException.class);
    }

    @Test
    void rejectsAmbiguousTariffsInsteadOfSelectingArbitrarily() {
        var weight = new BigDecimal("2");
        when(rates.findMatchingRates(1000000, weight)).thenReturn(List.of(rate("10", "2.5"), rate("20", "3")));

        assertThatThrownBy(() -> freight.calculateAmount("01000000", weight))
                .isInstanceOf(ConflictingFreightRatesException.class);
    }

    @Test
    void storesQuoteForRequesterWithBackendPriceAndConfiguredExpiration() {
        var customer = new User("Customer", "customer@example.test", "hash", UserRole.CUSTOMER);
        var weight = new BigDecimal("2");
        when(users.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(rates.findMatchingRates(1000000, weight)).thenReturn(List.of(rate("10", "2.5")));
        when(quotes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var quote = freight.createQuote(customer.getId(), "01000000", weight);

        verify(quotes).save(quote);
        assertThat(quote.getId()).isNotNull();
        assertThat(quote.getRequester()).isSameAs(customer);
        assertThat(quote.getDestinationPostalCode()).isEqualTo("01000000");
        assertThat(quote.getWeightKg()).isEqualByComparingTo("2");
        assertThat(quote.getAmount()).isEqualByComparingTo("15.00");
        assertThat(quote.getCreatedAt()).isEqualTo(now);
        assertThat(quote.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
    }

    @Test
    void rejectsDeletedRequesterBeforeCalculatingOrStoring() {
        assertThatThrownBy(() -> freight.createQuote(UUID.randomUUID(), "01000000", new BigDecimal("2")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(rates, quotes);
    }

    @Test
    void doesNotStoreQuoteWhenNoTariffApplies() {
        var customer = new User("Customer", "customer@example.test", "hash", UserRole.CUSTOMER);
        when(users.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(rates.findMatchingRates(anyInt(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> freight.createQuote(customer.getId(), "01000000", new BigDecimal("31")))
                .isInstanceOf(FreightRateUnavailableException.class);
        verify(quotes, never()).save(any(FreightQuote.class));
    }

    private FreightRate rate(String base, String perKg) {
        return new FreightRate(0, 99999999, BigDecimal.ZERO, new BigDecimal("1000"),
                new BigDecimal(base), new BigDecimal(perKg));
    }
}
