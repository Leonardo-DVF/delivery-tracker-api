package io.github.leonardodvf.deliverytracker.freight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "freight_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreightRate {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int postalStart;

    @Column(nullable = false)
    private int postalEnd;

    @Column(nullable = false, precision = 7, scale = 3)
    private BigDecimal weightMin;

    @Column(nullable = false, precision = 7, scale = 3)
    private BigDecimal weightMax;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerKg;

    public FreightRate(int postalStart, int postalEnd, BigDecimal weightMin, BigDecimal weightMax,
                       BigDecimal basePrice, BigDecimal pricePerKg) {
        this.id = UUID.randomUUID();
        this.postalStart = postalStart;
        this.postalEnd = postalEnd;
        this.weightMin = weightMin;
        this.weightMax = weightMax;
        this.basePrice = basePrice;
        this.pricePerKg = pricePerKg;
    }
}
