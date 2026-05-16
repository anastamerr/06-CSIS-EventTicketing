package com.team06.eventticketing.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtGatewayFilterTest {

    private static HttpServer backend;
    private static String backendBaseUrl;

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeAll
    static void startBackend() throws IOException {
        ensureBackendStarted();
    }

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterAll
    static void stopBackend() {
        if (backend != null) {
            backend.stop(0);
        }
    }

    @DynamicPropertySource
    static void registerGatewayRoutes(DynamicPropertyRegistry registry) {
        try {
            ensureBackendStarted();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start gateway filter test backend", exception);
        }
        registry.add("USER_SERVICE_URI", () -> backendBaseUrl);
        registry.add("EVENT_SERVICE_URI", () -> "http://localhost:65531");
        registry.add("BOOKING_SERVICE_URI", () -> "http://localhost:65532");
        registry.add("TICKET_SERVICE_URI", () -> "http://localhost:65533");
        registry.add("SALES_SERVICE_URI", () -> "http://localhost:65534");
    }

    @Test
    void authenticatedRequestsForwardJwtIdentityAndCorrelationHeaders() {
        webTestClient.get()
                .uri("/api/users/headers")
                .header("Authorization", "Bearer " + testJwt())
                .header("X-User-Id", "spoofed")
                .header("X-User-Role", "ADMIN")
                .header("X-Correlation-ID", "corr-123")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("uid=42;role=USER;corr=corr-123"));
    }

    @Test
    void authenticatedRequestsGenerateCorrelationIdWhenMissing() {
        webTestClient.get()
                .uri("/api/users/headers")
                .header("Authorization", "Bearer " + testJwt())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .startsWith("uid=42;role=USER;corr=")
                        .doesNotEndWith("corr="));
    }

    @Test
    void authBypassForwardsOnlyCorrelationHeader() {
        webTestClient.get()
                .uri("/api/auth/headers")
                .header("X-User-Id", "spoofed")
                .header("X-User-Role", "ADMIN")
                .header("X-Correlation-ID", "corr-auth")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("uid=missing;role=missing;corr=corr-auth"));
    }

    @Test
    void missingTokenIsUnauthorizedAndReturnsCorrelationHeader() {
        webTestClient.get()
                .uri("/api/users/headers")
                .header("X-Correlation-ID", "corr-denied")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-ID", "corr-denied");
    }

    private static String testJwt() {
        String secret = "bWlsZXN0b25lLTItc2hhcmVkLXNlY3JldC0zMi1ieXRlcy1taW4h";
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        Key key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .subject("test@example.com")
                .claim("uid", 42L)
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private static void ensureBackendStarted() throws IOException {
        if (backend != null) {
            return;
        }
        backend = HttpServer.create(new InetSocketAddress(0), 0);
        backend.createContext("/api/users/headers", new HeaderEchoHandler());
        backend.createContext("/api/auth/headers", new HeaderEchoHandler());
        backend.start();
        backendBaseUrl = "http://localhost:" + backend.getAddress().getPort();
    }

    private static final class HeaderEchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = "uid=" + firstHeader(exchange, "X-User-Id")
                    + ";role=" + firstHeader(exchange, "X-User-Role")
                    + ";corr=" + firstHeader(exchange, "X-Correlation-ID");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }

        private String firstHeader(HttpExchange exchange, String name) {
            String value = exchange.getRequestHeaders().getFirst(name);
            return value == null || value.isBlank() ? "missing" : value;
        }
    }
}
