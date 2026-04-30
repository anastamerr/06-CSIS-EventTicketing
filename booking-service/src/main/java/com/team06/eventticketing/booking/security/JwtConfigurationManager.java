package com.team06.eventticketing.booking.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;
    private final String secret;
    private final long expirationMs;

    private JwtConfigurationManager() {
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            secret = envSecret.trim();
        } else {
            secret = "7zvE7YVtVltaZG9tcGxleVNlY3JldEtleTEyMzQ1Njc4OQ==";
        }

        String envExpiration = System.getenv("JWT_EXPIRATION_MS");
        long expirationValue = 86400000L;
        if (envExpiration != null && !envExpiration.isBlank()) {
            try {
                expirationValue = Long.parseLong(envExpiration.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        expirationMs = expirationValue;
    }

    public static JwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (JwtConfigurationManager.class) {
                if (instance == null) {
                    instance = new JwtConfigurationManager();
                }
            }
        }
        return instance;
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public byte[] getSecretBytes() {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
