package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.shared.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class ChallengeCrypto {
    private static final int SECRET_BYTES = 32;
    private final SecureRandom random = new SecureRandom();
    private final byte[] hmacKey;

    public ChallengeCrypto(AppProperties properties) {
        this.hmacKey = Base64.getDecoder().decode(properties.getSecurity().getMfa().getHmacSecret());
        if (hmacKey.length < 32) {
            throw new IllegalArgumentException("La clave HMAC MFA debe contener al menos 32 bytes");
        }
    }

    public String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String newCode() {
        int value = random.nextInt(1_000_000);
        return String.format(java.util.Locale.ROOT, "%06d", value);
    }

    public String hashSecret(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }

    public String codeHmac(String id, ChallengePurpose purpose, int generation, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            String message = id + ":" + purpose.name() + ":" + generation + ":" + code;
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA-256 no disponible", exception);
        }
    }

    public boolean matchesSecret(String supplied, String expectedHash) {
        if (supplied == null || expectedHash == null) return false;
        return constantTimeEquals(hashSecret(supplied), expectedHash);
    }

    public boolean matchesCode(String id, ChallengePurpose purpose, int generation,
                               String supplied, String expectedHmac) {
        if (supplied == null || !supplied.matches("[0-9]{6}") || expectedHmac == null) return false;
        return constantTimeEquals(codeHmac(id, purpose, generation, supplied), expectedHmac);
    }

    private boolean constantTimeEquals(String actual, String expected) {
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(actual), HexFormat.of().parseHex(expected));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
