package io.github.leonardodvf.deliverytracker;

import io.github.leonardodvf.deliverytracker.freight.entity.FreightRate;
import io.github.leonardodvf.deliverytracker.freight.exception.FreightRateUnavailableException;
import io.github.leonardodvf.deliverytracker.freight.repository.FreightRateRepository;
import io.github.leonardodvf.deliverytracker.freight.service.FreightService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql("/db/local/V3__seed_fictional_freight_rates.sql")
class FreightIntegrationTests extends JwtTestSupport {

    @Autowired private FreightRateRepository rates;
    @Autowired private FreightService freight;

    @ParameterizedTest
    @CsvSource({
            "00000000, 0.001, 10.00",
            "00000000, 0.002, 10.01",
            "59999999, 5.000, 22.50",
            "59999999, 5.001, 35.00",
            "60000000, 0.001, 15.00",
            "60000000, 5.000, 35.00",
            "60000000, 5.001, 55.01",
            "99999999, 30.000, 180.00"
    })
    void appliesExactPostalAndWeightBoundariesFromDemoTariffs(String postalCode, String weight, String expected) {
        assertThat(freight.calculateAmount(postalCode, new BigDecimal(weight))).isEqualByComparingTo(expected);
    }

    @Test
    void rejectsWeightsOutsideDemoCoverage() {
        assertThatThrownBy(() -> freight.calculateAmount("01000000", new BigDecimal("30.001")))
                .isInstanceOf(FreightRateUnavailableException.class);
    }

    @Test
    void databaseRejectsRulesOverlappingInBothPostalAndWeightRanges() {
        var overlap = new FreightRate(1000000, 2000000, new BigDecimal("1"), new BigDecimal("2"),
                new BigDecimal("1"), new BigDecimal("1"));

        assertThatThrownBy(() -> rates.saveAndFlush(overlap))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_freight_rates_no_overlap");
    }

    @Test
    void databaseAcceptsWeightBandsThatOnlyTouchAtAnExclusiveLowerBound() {
        rates.saveAndFlush(new FreightRate(0, 59999999, new BigDecimal("30"), new BigDecimal("50"),
                new BigDecimal("40"), new BigDecimal("6")));

        assertThat(rates.count()).isEqualTo(5);
        assertThat(freight.calculateAmount("01000000", new BigDecimal("30"))).isEqualByComparingTo("110.00");
        assertThat(freight.calculateAmount("01000000", new BigDecimal("30.001"))).isEqualByComparingTo("220.01");
    }

    @Test
    void databaseRejectsNegativePrices() {
        var invalid = new FreightRate(0, 99999999, new BigDecimal("30"), new BigDecimal("50"),
                new BigDecimal("-1"), new BigDecimal("1"));

        assertThatThrownBy(() -> rates.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_freight_rates_nonnegative_prices");
    }
}
