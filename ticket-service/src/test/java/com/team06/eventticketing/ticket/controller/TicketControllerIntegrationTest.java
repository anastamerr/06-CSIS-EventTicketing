package com.team06.eventticketing.ticket.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TicketControllerIntegrationTest.FixedClockConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class TicketControllerIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-29T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    venue VARCHAR(255) NOT NULL,
                    details JSONB
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    id BIGINT PRIMARY KEY,
                    event_id BIGINT
                )
                """);
        jdbcTemplate.execute("TRUNCATE TABLE tickets, bookings, events RESTART IDENTITY CASCADE");
        ticketRepository.deleteAll();
    }

    @Test
    void purgeDeletesExpiredTicketsOlderThanCutoffAndLeavesValidTickets() throws Exception {
        LocalDateTime olderThanCutoff = now().minusDays(60);
        saveTickets(TicketStatus.EXPIRED, 7, olderThanCutoff, "expired-old");
        saveTickets(TicketStatus.VALID, 3, olderThanCutoff, "valid-old");

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(7));

        assertStatusCount(TicketStatus.EXPIRED, 0);
        assertStatusCount(TicketStatus.VALID, 3);
        assertRepositoryCount(3);
    }

    @Test
    void purgeDeletesCancelledTicketsOlderThanCutoff() throws Exception {
        saveTickets(TicketStatus.CANCELLED, 4, now().minusDays(45), "cancelled-old");
        saveTickets(TicketStatus.VALID, 1, now().minusDays(45), "valid-control");

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(4));

        assertStatusCount(TicketStatus.CANCELLED, 0);
        assertStatusCount(TicketStatus.VALID, 1);
        assertRepositoryCount(1);
    }

    @Test
    void purgeDoesNotDeleteExpiredTicketsNewerThanCutoff() throws Exception {
        saveTickets(TicketStatus.EXPIRED, 2, now().minusDays(10), "expired-new");

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(0));

        assertStatusCount(TicketStatus.EXPIRED, 2);
        assertRepositoryCount(2);
    }

    @Test
    void purgeDoesNotDeleteUsedTicketsOlderThanCutoff() throws Exception {
        saveTickets(TicketStatus.USED, 3, now().minusDays(60), "used-old");

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(0));

        assertStatusCount(TicketStatus.USED, 3);
        assertRepositoryCount(3);
    }

    @Test
    void purgeDoesNotDeleteValidTicketsOlderThanCutoff() throws Exception {
        saveTickets(TicketStatus.VALID, 2, now().minusDays(60), "valid-old");

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(0));

        assertStatusCount(TicketStatus.VALID, 2);
        assertRepositoryCount(2);
    }

    @Test
    void purgeWithZeroDaysDeletesOnlyExpiredOrCancelledTicketsOlderThanNow() throws Exception {
        LocalDateTime now = now();
        saveTicket("expired-before-now", TicketStatus.EXPIRED, now.minusMinutes(1));
        saveTicket("cancelled-before-now", TicketStatus.CANCELLED, now.minusSeconds(1));
        saveTicket("expired-at-now", TicketStatus.EXPIRED, now);
        saveTicket("valid-before-now", TicketStatus.VALID, now.minusDays(1));

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(2));

        assertStatusCount(TicketStatus.EXPIRED, 1);
        assertStatusCount(TicketStatus.CANCELLED, 0);
        assertStatusCount(TicketStatus.VALID, 1);
        assertRepositoryCount(2);
    }

    @Test
    void purgeRejectsNegativeOlderThanDays() throws Exception {
        saveTickets(TicketStatus.EXPIRED, 1, now().minusDays(90), "expired-old");

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "-1"))
                .andExpect(status().isBadRequest());

        assertStatusCount(TicketStatus.EXPIRED, 1);
        assertRepositoryCount(1);
    }

    @Test
    void purgeReturnsZeroWhenRepeatedAfterInitialDelete() throws Exception {
        saveTickets(TicketStatus.EXPIRED, 2, now().minusDays(90), "expired-old");

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(2));

        mockMvc.perform(delete("/api/tickets/purge").param("olderThanDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(0));

        assertRepositoryCount(0);
    }

    @Test
    void nearbyReturnsTicketsWithinRadiusSortedByDistance() throws Exception {
        insertEvent(100L, "Downtown Show", "Opera House", 30.0445, 31.2357);
        insertEvent(200L, "Far Show", "Desert Arena", 29.9765, 31.1313);
        insertBooking(10L, 100L);
        insertBooking(20L, 200L);

        ticketRepository.saveAllAndFlush(List.of(
                nearbyTicket(10L, "TIX-NEAR-1", "Near One"),
                nearbyTicket(10L, "TIX-NEAR-2", "Near Two"),
                nearbyTicket(20L, "TIX-FAR-1", "Far One")
        ));

        mockMvc.perform(get("/api/tickets/nearby")
                        .param("lat", "30.0444")
                        .param("lon", "31.2357")
                        .param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticketId").isNumber())
                .andExpect(jsonPath("$[0].attendeeName").value("Near One"))
                .andExpect(jsonPath("$[0].bookingId").value(10))
                .andExpect(jsonPath("$[0].eventName").value("Downtown Show"))
                .andExpect(jsonPath("$[0].eventLat").value(30.0445))
                .andExpect(jsonPath("$[0].eventLon").value(31.2357))
                .andExpect(jsonPath("$[0].distanceKm").isNumber())
                .andExpect(jsonPath("$[1].attendeeName").value("Near Two"));
    }

    @Test
    void nearbyRejectsInvalidLatitude() throws Exception {
        mockMvc.perform(get("/api/tickets/nearby")
                        .param("lat", "95")
                        .param("lon", "31.2357")
                        .param("radiusKm", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issueTicketWithMetadataCreatesValidTicket() throws Exception {
        insertBooking(1L, 100L);

        String payload = "{\"attendeeName\":\"Ahmed\",\"ticketCode\":\"TIX-2026-001\",\"metadata\":{\"seatNumber\":\"A12\"}}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/tickets/booking/1")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.bookingId").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.attendeeName").value("Ahmed"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.ticketCode").value("TIX-2026-001"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("VALID"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.metadata.seatNumber").value("A12"));

        Ticket savedTicket = ticketRepository.findByTicketCode("TIX-2026-001").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1L, savedTicket.getBookingId());
        org.junit.jupiter.api.Assertions.assertEquals(now(), savedTicket.getIssuedAt());
        org.junit.jupiter.api.Assertions.assertEquals("A12", savedTicket.getMetadata().get("seatNumber"));
    }

    @Test
    void issueTicketWithMetadataNotFoundBooking() throws Exception {
        String payload = "{\"attendeeName\":\"Ahmed\",\"ticketCode\":\"TIX-2026-002\"}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/tickets/booking/999")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTicketsHistoryByDateRangeAndStatus() throws Exception {
        insertBooking(1L, 100L);
        saveTicket("TIX-2026-100", TicketStatus.VALID, LocalDateTime.of(2026, 3, 19, 9, 0));
        saveTicket("TIX-2026-101", TicketStatus.USED, LocalDateTime.of(2026, 3, 25, 14, 30));
        saveTicket("TIX-2026-102", TicketStatus.VALID, LocalDateTime.of(2026, 3, 30, 23, 59, 59, 500_000_000));
        saveTicket("TIX-2026-103", TicketStatus.VALID, LocalDateTime.of(2026, 2, 28, 23, 59));

        mockMvc.perform(get("/api/tickets/history")
                        .param("startDate", "2026-03-19")
                        .param("endDate", "2026-03-30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(3))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].ticketCode").value("TIX-2026-100"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[1].ticketCode").value("TIX-2026-101"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[2].ticketCode").value("TIX-2026-102"));

        mockMvc.perform(get("/api/tickets/history")
                        .param("startDate", "2026-03-19")
                        .param("endDate", "2026-03-30")
                        .param("status", "VALID"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].ticketCode").value("TIX-2026-100"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[1].ticketCode").value("TIX-2026-102"));
    }

    @Test
    void getTicketsHistoryWithInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/tickets/history")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-03-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTicketsHistoryReturnsEmptyListWhenNoTicketsMatch() throws Exception {
        insertBooking(1L, 100L);
        saveTicket("TIX-2026-200", TicketStatus.VALID, LocalDateTime.of(2026, 2, 15, 12, 0));

        mockMvc.perform(get("/api/tickets/history")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchTicketsByMetadataSupportsEqGtLtAndSkipsNonNumericValues() throws Exception {
        ticketRepository.saveAllAndFlush(List.of(
                ticketWithMetadata("TIX-GATE-1", Map.of("gate", 1)),
                ticketWithMetadata("TIX-GATE-3", Map.of("gate", 3)),
                ticketWithMetadata("TIX-GATE-5", Map.of("gate", 5)),
                ticketWithMetadata("TIX-GATE-NAN", Map.of("gate", "VIP")),
                ticketWithMetadata("TIX-NO-GATE", Map.of("section", "A"))
        ));

        mockMvc.perform(get("/api/tickets/metadata/search")
                        .param("key", "gate")
                        .param("operator", "gt")
                        .param("value", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].metadata.gate").value(3))
                .andExpect(jsonPath("$[1].metadata.gate").value(5));

        mockMvc.perform(get("/api/tickets/metadata/search")
                        .param("key", "gate")
                        .param("operator", "eq")
                        .param("value", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].metadata.gate").value(1));
        
        mockMvc.perform(get("/api/tickets/metadata/search")
                        .param("key", "gate")
                        .param("operator", "lt")
                        .param("value", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].metadata.gate").value(1))
                .andExpect(jsonPath("$[1].metadata.gate").value(3));
    }

    @Test
    void searchTicketsByMetadataRejectsInvalidOperator() throws Exception {
        mockMvc.perform(get("/api/tickets/metadata/search")
                        .param("key", "gate")
                        .param("operator", "xyz")
                        .param("value", "1"))
                .andExpect(status().isBadRequest());
    }

    private void saveTickets(TicketStatus status, int count, LocalDateTime issuedAt, String codePrefix) {
        List<Ticket> tickets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            tickets.add(newTicket(codePrefix + "-" + index, status, issuedAt));
        }
        ticketRepository.saveAllAndFlush(tickets);
    }

    private void saveTicket(String ticketCode, TicketStatus status, LocalDateTime issuedAt) {
        ticketRepository.saveAndFlush(newTicket(ticketCode, status, issuedAt));
    }

    private Ticket newTicket(String ticketCode, TicketStatus status, LocalDateTime issuedAt) {
        Ticket ticket = new Ticket();
        ticket.setBookingId(1L);
        ticket.setAttendeeName("Attendee " + ticketCode);
        ticket.setTicketCode(ticketCode);
        ticket.setStatus(status);
        ticket.setIssuedAt(issuedAt);
        ticket.setMetadata(Map.of("ticketCode", ticketCode));
        return ticket;
    }

    private Ticket ticketWithMetadata(String ticketCode, Map<String, Object> metadata) {
        Ticket ticket = new Ticket();
        ticket.setBookingId(1L);
        ticket.setAttendeeName("Attendee " + ticketCode);
        ticket.setTicketCode(ticketCode);
        ticket.setStatus(TicketStatus.VALID);
        ticket.setIssuedAt(now());
        ticket.setMetadata(new LinkedHashMap<>(metadata));
        return ticket;
    }

    private Ticket nearbyTicket(Long bookingId, String ticketCode, String attendeeName) {
        Ticket ticket = new Ticket();
        ticket.setBookingId(bookingId);
        ticket.setAttendeeName(attendeeName);
        ticket.setTicketCode(ticketCode);
        ticket.setStatus(TicketStatus.VALID);
        ticket.setIssuedAt(now());
        ticket.setMetadata(new LinkedHashMap<>(Map.of("ticketCode", ticketCode)));
        return ticket;
    }

    private void insertBooking(Long bookingId, Long eventId) {
        jdbcTemplate.update("INSERT INTO bookings (id, event_id) VALUES (?, ?)", bookingId, eventId);
    }

    private void insertEvent(Long eventId, String name, String venue, double venueLat, double venueLon) {
        jdbcTemplate.update(
                "INSERT INTO events (id, name, venue, details) VALUES (?, ?, ?, CAST(? AS jsonb))",
                eventId,
                name,
                venue,
                String.format("{\"venueLat\": %.6f, \"venueLon\": %.6f}", venueLat, venueLon)
        );
    }

    private void assertStatusCount(TicketStatus status, long expectedCount) {
        long actualCount = ticketRepository.findAll().stream()
                .filter(ticket -> ticket.getStatus() == status)
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(expectedCount, actualCount);
    }

    private void assertRepositoryCount(long expectedCount) {
        org.junit.jupiter.api.Assertions.assertEquals(expectedCount, ticketRepository.count());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(FIXED_CLOCK);
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
