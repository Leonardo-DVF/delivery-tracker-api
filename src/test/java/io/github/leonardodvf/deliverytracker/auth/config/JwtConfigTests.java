package io.github.leonardodvf.deliverytracker.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTests {

    @Test
    void rejectsMismatchedKeyPair() throws Exception {
        var first = keys(2048);
        var second = keys(2048);
        var properties = properties(first.getPrivate().getEncoded(), second.getPublic().getEncoded());

        assertThatThrownBy(() -> new JwtConfig().jwtKeys(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("matching RSA keys");
    }

    @Test
    void rejectsRsaKeysBelow2048Bits() throws Exception {
        var pair = keys(1024);
        var properties = properties(pair.getPrivate().getEncoded(), pair.getPublic().getEncoded());

        assertThatThrownBy(() -> new JwtConfig().jwtKeys(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2048 bits");
    }

    private KeyPair keys(int bits) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private JwtProperties properties(byte[] privateKey, byte[] publicKey) {
        return new JwtProperties("test-issuer", resource("PRIVATE KEY", privateKey),
                resource("PUBLIC KEY", publicKey), 3600);
    }

    private ByteArrayResource resource(String type, byte[] bytes) {
        var pem = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
    }
}
