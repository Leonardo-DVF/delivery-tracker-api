package io.github.leonardodvf.deliverytracker;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

public abstract class JwtTestSupport {

    private static final Path KEYS = generateKeys();

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry properties) {
        properties.add("app.jwt.private-key", () -> KEYS.resolve("private.pem").toUri().toString());
        properties.add("app.jwt.public-key", () -> KEYS.resolve("public.pem").toUri().toString());
        properties.add("app.jwt.issuer", () -> "delivery-tracker-test");
        properties.add("app.jwt.ttl-seconds", () -> 3600);
    }

    private static Path generateKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            var directory = Files.createTempDirectory("delivery-tracker-jwt-tests-");
            directory.toFile().deleteOnExit();
            writeKey(directory.resolve("private.pem"), "PRIVATE KEY", pair.getPrivate().getEncoded());
            writeKey(directory.resolve("public.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());
            return directory;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void writeKey(Path path, String type, byte[] encoded) throws Exception {
        var contents = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
        Files.writeString(path, contents, StandardCharsets.US_ASCII);
        path.toFile().deleteOnExit();
    }
}
