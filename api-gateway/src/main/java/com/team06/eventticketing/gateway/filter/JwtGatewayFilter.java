package com.team06.eventticketing.gateway.filter;

import com.team06.eventticketing.common.auth.JwtService;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtGatewayFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = correlationId(exchange);
        String path = exchange.getRequest().getURI().getPath();

        if (path.equals("/api/auth") || path.startsWith("/api/auth/") || path.startsWith("/actuator/health")) {
            return forward(exchange, chain, correlationId, null, null);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, correlationId);
        }

        try {
            Claims claims = jwtService.parseClaims(authorization.substring(BEARER_PREFIX.length()));
            return forward(
                    exchange,
                    chain,
                    correlationId,
                    String.valueOf(jwtService.extractUserId(claims)),
                    jwtService.extractRole(claims));
        } catch (RuntimeException exception) {
            return unauthorized(exchange, correlationId);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private Mono<Void> forward(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String correlationId,
            String userId,
            String role
    ) {
        ServerHttpRequest.Builder request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(CORRELATION_ID_HEADER, correlationId);
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    if (userId != null) {
                        headers.set(USER_ID_HEADER, userId);
                    }
                    if (role != null) {
                        headers.set(USER_ROLE_HEADER, role);
                    }
                });
        return chain.filter(exchange.mutate().request(request.build()).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String correlationId) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        return exchange.getResponse().setComplete();
    }

    private String correlationId(ServerWebExchange exchange) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        return incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
    }
}
