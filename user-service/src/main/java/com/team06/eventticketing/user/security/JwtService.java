package com.team06.eventticketing.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtConfigurationManager config;
    private final Key signingKey;

    public JwtService() {
        this.config = JwtConfigurationManager.getInstance();
        this.signingKey = Keys.hmacShaKeyFor(config.getSecretBytes());
    }

    public String generateToken(Long uid, String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(config.getExpirationMs())))
                .claim("uid", uid)
                .claim("role", role)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validateToken(String token) {
        Jws<Claims> jwt = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
        return jwt.getBody();
    }

    public long getExpirationMs() {
        return config.getExpirationMs();
    }
}
