package io.github.leonardodvf.deliverytracker.freight.config;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.freight")
public record FreightProperties(@NotNull @DurationMin(minutes = 1) @DurationMax(hours = 24) Duration quoteTtl) {
}
