package io.github.leonardodvf.deliverytracker.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/** Plays the carrier in tests: signs {@code <timestamp>.<body>} independently of the production verifier. */
public final class CarrierWebhookSigner {

    /** Same value as src/test/resources/application.properties. */
    public static final String TEST_SECRET = "test-only-carrier-webhook-secret-0123456789";

    private CarrierWebhookSigner() {
    }

    public static String timestamp(Instant at) {
        return Long.toString(at.getEpochSecond());
    }

    public static String signature(String secret, String timestamp, byte[] body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String signature(String timestamp, String body) {
        return signature(TEST_SECRET, timestamp, body.getBytes(StandardCharsets.UTF_8));
    }
}
