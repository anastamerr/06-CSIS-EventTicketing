package com.team06.eventticketing.gateway.routes;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceRouteOwnershipTest {

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
            throw new IllegalStateException("Failed to start user-service test backend", exception);
        }
        registry.add("USER_SERVICE_URI", () -> backendBaseUrl);
        registry.add("EVENT_SERVICE_URI", () -> "http://localhost:65531");
        registry.add("BOOKING_SERVICE_URI", () -> "http://localhost:65532");
        registry.add("TICKET_SERVICE_URI", () -> "http://localhost:65533");
        registry.add("SALES_SERVICE_URI", () -> "http://localhost:65534");
    }

    @Test
    void documentsUserServiceGatewayPaths() {
        assertThat(UserServiceRouteOwnership.ROUTE_ID).isEqualTo("user-service");
        assertThat(UserServiceRouteOwnership.USER_SERVICE_URI).isEqualTo("http://user-service:8080");
        assertThat(UserServiceRouteOwnership.OWNED_PATH_PATTERNS)
                .containsExactly("/api/auth/**", "/api/users/**");
        assertThat(UserServiceRouteOwnership.USER_EVENTS_EXCHANGE).isEqualTo("user.events");
        assertThat(UserServiceRouteOwnership.USER_BOOKING_SAGA_QUEUE).isEqualTo("user.booking.saga-listener");
        assertThat(UserServiceRouteOwnership.USER_BOOKING_SAGA_DLQ).isEqualTo("user.booking.saga-listener.dlq");
    }

    @Test
    void proxiesUserEndpointsThroughGateway() {
        webTestClient.get()
                .uri("/api/users/health")
                .header("Authorization", "Bearer " + testJwt())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("USER-HEALTH");
    }

    @Test
    void proxiesAuthEndpointsThroughGateway() {
        webTestClient.get()
                .uri("/api/auth/ping")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("AUTH-PING");
    }

    @Test
    void leavesUnknownPathsUnrouted() {
        webTestClient.get()
                .uri("/api/unknown")
                .exchange()
                .expectStatus().isNotFound();
    }

    private static String testJwt() {
        String secret = "bWlsZXN0b25lLTItc2hhcmVkLXNlY3JldC0zMi1ieXRlcy1taW4h";
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        Key key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .subject("test@example.com")
                .claim("uid", 1L)
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
        backend.createContext("/api/users/health", new PlainTextHandler(200, "USER-HEALTH"));
        backend.createContext("/api/auth/ping", new PlainTextHandler(200, "AUTH-PING"));
        backend.start();
        backendBaseUrl = "http://localhost:" + backend.getAddress().getPort();
    }

    private record PlainTextHandler(int status, String body) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}
