package com.team06.eventticketing.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public String generateToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        JwtConfigurationManager configuration = JwtConfigurationManager.getInstance();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(configuration.getExpirationMs())))
                .claim("uid", userId)
                .claim("role", role)
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(Claims claims) {
        Object userId = claims.get("uid");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(userId));
    }

    public String extractRole(Claims claims) {
        Object role = claims.get("role");
        return role == null ? null : role.toString();
    }

    public Map<String, Object> toTokenPayload(String token) {
        Claims claims = parseClaims(token);
        return Map.of(
                "uid", extractUserId(claims),
                "email", claims.getSubject(),
                "role", extractRole(claims));
    }

    private Key signingKey() {
        String secret = JwtConfigurationManager.getInstance().getSecret();
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
