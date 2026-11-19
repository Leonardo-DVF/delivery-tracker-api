package io.github.leonardodvf.deliverytracker.webhook.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Shared secret for HMAC-SHA256 webhook signatures and how far the signed timestamp may drift from server time.
 * The application refuses to start without a secret of at least 32 characters.
 */
@Validated
@ConfigurationProperties("app.carrier.webhook")
public record CarrierWebhookProperties(@NotBlank @Size(min = 32, max = 512) String secret,
                                       @NotNull @DurationMin(seconds = 30) @DurationMax(minutes = 15) Duration tolerance) {

    /** Keeps the secret out of logs and failure analysis output. */
    @Override
    public String toString() {
        return "CarrierWebhookProperties[secret=****, tolerance=" + tolerance + "]";
    }
}
