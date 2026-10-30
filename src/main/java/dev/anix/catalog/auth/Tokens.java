package dev.anix.catalog.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Opaque API tokens: random 32 bytes, only the SHA-256 hash is ever stored. */
public final class Tokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {}

    public static String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Extracts the token from an Authorization header, or null if it is not a Bearer header. */
    public static String fromHeader(String header) {
        if (header == null) return null;
        String h = header.trim();
        if (h.length() < 8 || !h.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = h.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
