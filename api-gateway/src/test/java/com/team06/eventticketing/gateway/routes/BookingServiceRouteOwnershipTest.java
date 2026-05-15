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
import java.util.List;
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
class BookingServiceRouteOwnershipTest {

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
            throw new IllegalStateException("Failed to start booking-service test backend", exception);
        }
        registry.add("USER_SERVICE_URI", () -> "http://localhost:65531");
        registry.add("EVENT_SERVICE_URI", () -> "http://localhost:65532");
        registry.add("BOOKING_SERVICE_URI", () -> backendBaseUrl);
        registry.add("TICKET_SERVICE_URI", () -> "http://localhost:65533");
        registry.add("SALES_SERVICE_URI", () -> "http://localhost:65534");
    }

    @Test
    void documentsBookingServiceGatewayAndMessagingOwnership() {
        assertThat(BookingServiceRouteOwnership.ROUTE_ID).isEqualTo("booking-service");
        assertThat(BookingServiceRouteOwnership.BOOKING_SERVICE_URI).isEqualTo("http://booking-service:8080");
        assertThat(BookingServiceRouteOwnership.OWNED_PATH_PATTERNS).containsExactly("/api/bookings/**");
        assertThat(BookingServiceRouteOwnership.COVERED_BOOKING_ENDPOINTS).containsAll(List.of(
                "GET /api/bookings/{id}",
                "PUT /api/bookings/{bookingId}/confirm",
                "PUT /api/bookings/{id}/complete",
                "PUT /api/bookings/{id}/cancel"));
        assertThat(BookingServiceRouteOwnership.BOOKING_EVENTS_EXCHANGE).isEqualTo("booking.events");
        assertThat(BookingServiceRouteOwnership.BOOKING_SAGA_FEEDBACK_QUEUE).isEqualTo("booking.saga-feedback");
        assertThat(BookingServiceRouteOwnership.BOOKING_SAGA_FEEDBACK_DLQ).isEqualTo("booking.saga-feedback.dlq");
    }

    @Test
    void proxiesBookingReadEndpointThroughGateway() {
        webTestClient.get()
                .uri("/api/bookings/1")
                .header("Authorization", "Bearer " + testJwt())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("BOOKING-READ");
    }

    @Test
    void proxiesBookingConfirmationCompletionAndCancellationThroughGateway() {
        for (String path : List.of(
                "/api/bookings/1/confirm?eventId=10",
                "/api/bookings/1/complete",
                "/api/bookings/1/cancel")) {
            webTestClient.put()
                    .uri(path)
                    .header("Authorization", "Bearer " + testJwt())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("BOOKING-WRITE");
        }
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
        backend.createContext("/api/bookings/1", new PlainTextHandler(200, "BOOKING-READ"));
        backend.createContext("/api/bookings/1/confirm", new PlainTextHandler(200, "BOOKING-WRITE"));
        backend.createContext("/api/bookings/1/complete", new PlainTextHandler(200, "BOOKING-WRITE"));
        backend.createContext("/api/bookings/1/cancel", new PlainTextHandler(200, "BOOKING-WRITE"));
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
