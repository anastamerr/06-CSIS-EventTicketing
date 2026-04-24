package com.team06.eventticketing.common.auth;

public final class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    private String secret;
    private long expirationMs;

    private JwtConfigurationManager() {
        this.secret = "bWlsZXN0b25lLTItc2hhcmVkLXNlY3JldC0zMi1ieXRlcy1taW4h";
        this.expirationMs = 86_400_000L;
    }

    public static JwtConfigurationManager getInstance() {
        JwtConfigurationManager local = instance;
        if (local == null) {
            synchronized (JwtConfigurationManager.class) {
                local = instance;
                if (local == null) {
                    local = new JwtConfigurationManager();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void initConfig(String secret, long expirationMs) {
        JwtConfigurationManager manager = getInstance();
        synchronized (JwtConfigurationManager.class) {
            if (secret != null && !secret.isBlank()) {
                manager.secret = secret;
            }
            if (expirationMs > 0) {
                manager.expirationMs = expirationMs;
            }
        }
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
