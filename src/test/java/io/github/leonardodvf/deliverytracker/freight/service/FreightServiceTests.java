package io.github.leonardodvf.deliverytracker.freight.service;

import io.github.leonardodvf.deliverytracker.freight.entity.FreightRate;
import io.github.leonardodvf.deliverytracker.freight.exception.ConflictingFreightRatesException;
import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FreightServiceTests {

    private final FreightRateRepository rates = mock(FreightRateRepository.class);
    private final FreightService freight = new FreightService(rates);

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

    private FreightRate rate(String base, String perKg) {
        return new FreightRate(0, 99999999, BigDecimal.ZERO, new BigDecimal("1000"),
                new BigDecimal(base), new BigDecimal(perKg));
    }
}
