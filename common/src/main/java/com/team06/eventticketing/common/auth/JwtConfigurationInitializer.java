package com.team06.eventticketing.common.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfigurationInitializer {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    @PostConstruct
    void initialize() {
        JwtConfigurationManager.initConfig(secret, expirationMs);
    }
}
