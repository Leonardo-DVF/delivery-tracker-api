package io.github.leonardodvf.deliverytracker.webhook.security;

import io.github.leonardodvf.deliverytracker.webhook.config.CarrierWebhookProperties;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookSignatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import static io.github.leonardodvf.deliverytracker.webhook.CarrierWebhookSigner.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureVerifierTests {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final String BODY = "{\"eventId\":\"evt-1\",\"status\":\"PICKED_UP\"}";

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(
            new CarrierWebhookProperties(TEST_SECRET, Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC));
    private final String timestamp = timestamp(NOW);

    @Test
    void acceptsAValidSignatureInEitherHexCase() {
        var signature = signature(timestamp, BODY);

        assertThatCode(() -> verifier.verify(timestamp, signature, bytes(BODY))).doesNotThrowAnyException();
        assertThatCode(() -> verifier.verify(timestamp, "sha256=" + signature.substring(7).toUpperCase(Locale.ROOT), bytes(BODY)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsTimestampsExactlyAtTheToleranceInBothDirections() {
        for (var at : new Instant[]{NOW.minus(Duration.ofMinutes(5)), NOW.plus(Duration.ofMinutes(5))}) {
            var edge = timestamp(at);
            assertThatCode(() -> verifier.verify(edge, signature(edge, BODY), bytes(BODY))).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsTimestampsOneSecondOutsideTheTolerance() {
        for (var at : new Instant[]{NOW.minusSeconds(301), NOW.plusSeconds(301)}) {
            var stale = timestamp(at);
            assertRejected(stale, signature(stale, BODY), BODY, "timestamp outside tolerance");
        }
    }

    @Test
    void rejectsATamperedBody() {
        assertRejected(timestamp, signature(timestamp, BODY), BODY.replace("PICKED_UP", "DELIVERED"), "signature mismatch");
    }

    @Test
    void rejectsASignatureMadeForAnotherTimestamp() {
        var other = timestamp(NOW.minusSeconds(10));
        assertRejected(timestamp, signature(other, BODY), BODY, "signature mismatch");
    }

    @Test
    void rejectsASignatureMadeWithAnotherSecret() {
        var forged = signature("another-secret-with-at-least-32-characters!", timestamp, bytes(BODY));
        assertRejected(timestamp, forged, BODY, "signature mismatch");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "-1", "1.5", "1763553600000000", " 1763553600"})
    void rejectsMissingOrMalformedTimestamps(String value) {
        assertRejected(value, signature(timestamp, BODY), BODY, "missing or malformed timestamp");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sha256=", "sha256=abc", "sha1=0000000000000000000000000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "sha256=zz00000000000000000000000000000000000000000000000000000000000000"})
    void rejectsMissingOrMalformedSignatures(String value) {
        assertRejected(timestamp, value, BODY, "missing or malformed signature");
    }

    private void assertRejected(String timestamp, String signature, String body, String reason) {
        assertThatThrownBy(() -> verifier.verify(timestamp, signature, bytes(body)))
                .isInstanceOf(InvalidWebhookSignatureException.class)
                .hasFieldOrPropertyWithValue("reason", reason)
                .satisfies(ex -> {
                    var problem = ((InvalidWebhookSignatureException) ex).getBody();
                    assertThat(problem.getStatus()).isEqualTo(401);
                    assertThat(problem.getDetail()).isEqualTo("Invalid or missing webhook signature");
                });
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
