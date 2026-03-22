package au.com.transport.tapservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for hashing and masking PANs (Primary Account Numbers).
 * Hashing is one-way and produces a consistent output for the same input + salt.
 * Masking hides all but the last 4 digits of the PAN.
 */
@Component
public class PanTokeniser {

    private final String salt;

    public PanTokeniser(@Value("${app.security.pan-salt:default-salt-change-in-prod}") String salt) {
        this.salt = salt;
    }

    /**
     * One-way hash of PAN + salt.
     * Same PAN always produces same hash → usable as DB matching key.
     */
    public String hash(String pan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = pan.trim() + salt;
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Mask all but last 4 digits.
     * 5500005555555559 → ****5559
     */
    public String mask(String pan) {
        if (pan == null || pan.trim().length() < 4) {
            return "****";
        }
        String trimmed = pan.trim();
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
