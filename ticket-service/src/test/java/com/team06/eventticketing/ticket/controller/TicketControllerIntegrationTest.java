package com.team06.eventticketing.ticket.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TicketControllerIntegrationTest.FixedClockConfiguration.class)
@Testcontainers
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

    @BeforeEach
    void setUp() {
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
