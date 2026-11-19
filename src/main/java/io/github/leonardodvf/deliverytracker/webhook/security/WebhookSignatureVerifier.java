package io.github.leonardodvf.deliverytracker.webhook.security;

import io.github.leonardodvf.deliverytracker.webhook.config.CarrierWebhookProperties;
import io.github.leonardodvf.deliverytracker.webhook.exception.InvalidWebhookSignatureException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Verifies {@code X-Carrier-Signature: sha256=<hex>}, an HMAC-SHA256 of {@code <timestamp>.<raw body>}
 * keyed with the shared secret. Signing the timestamp lets old captured requests be refused, and the
 * raw bytes are signed so the check happens before any JSON parsing.
 */
@Component
public class WebhookSignatureVerifier {

    public static final String SIGNATURE_PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{1,12}");
    private static final Pattern SIGNATURE = Pattern.compile("sha256=[0-9a-fA-F]{64}");

    private final SecretKeySpec key;
    private final Duration tolerance;
    private final Clock clock;

    public WebhookSignatureVerifier(CarrierWebhookProperties properties, Clock clock) {
        this.key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.tolerance = properties.tolerance();
        this.clock = clock;
    }

    public void verify(String timestamp, String signature, byte[] body) {
        if (timestamp == null || !TIMESTAMP.matcher(timestamp).matches()) {
            throw new InvalidWebhookSignatureException("missing or malformed timestamp");
        }
        var drift = Duration.between(Instant.ofEpochSecond(Long.parseLong(timestamp)), clock.instant()).abs();
        if (drift.compareTo(tolerance) > 0) {
            throw new InvalidWebhookSignatureException("timestamp outside tolerance");
        }
        if (signature == null || !SIGNATURE.matcher(signature).matches()) {
            throw new InvalidWebhookSignatureException("missing or malformed signature");
        }
        var provided = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
        // Constant-time comparison, so response timing does not reveal how many bytes matched.
        if (!MessageDigest.isEqual(sign(timestamp, body), provided)) {
            throw new InvalidWebhookSignatureException("signature mismatch");
        }
    }

    private byte[] sign(String timestamp, byte[] body) {
        try {
            var mac = Mac.getInstance(ALGORITHM); // Mac is not thread-safe, so one instance per call.
            mac.init(key);
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return mac.doFinal(body);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }
}
