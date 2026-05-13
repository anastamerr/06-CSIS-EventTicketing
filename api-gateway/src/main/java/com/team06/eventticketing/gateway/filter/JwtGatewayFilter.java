package com.team06.eventticketing.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret:bWlsZXN0b25lLTItc2hhcmVkLXNlY3JldC0zMi1ieXRlcy1taW4h}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (path.startsWith("/api/auth/")) {
            return forwardWithCorrelationId(exchange, chain, null, null);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        Object uid = claims.get("uid");
        String userId = uid == null ? "" : String.valueOf(uid instanceof Number n ? n.longValue() : uid);
        Object role = claims.get("role");
        String userRole = role == null ? "" : role.toString();

        return forwardWithCorrelationId(exchange, chain, userId, userRole);
    }

    private Mono<Void> forwardWithCorrelationId(ServerWebExchange exchange,
                                                 GatewayFilterChain chain,
                                                 String userId,
                                                 String userRole) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest.Builder mutated = exchange.getRequest().mutate()
                .header("X-Correlation-ID", correlationId);

        if (userId != null) {
            mutated.header("X-User-Id", userId);
        }
        if (userRole != null) {
            mutated.header("X-User-Role", userRole);
        }

        return chain.filter(exchange.mutate().request(mutated.build()).build());
    }

    private Key signingKey() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(jwtSecret);
        } catch (IllegalArgumentException e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
