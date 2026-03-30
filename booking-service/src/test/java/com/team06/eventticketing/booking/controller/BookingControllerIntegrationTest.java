package com.team06.eventticketing.booking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BookingControllerIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("eventticketing")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ticket_sales (
                    id BIGSERIAL PRIMARY KEY,
                    booking_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    amount NUMERIC(19, 4) NOT NULL,
                    method VARCHAR(50) NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    transaction_details JSONB,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tickets (
                    id BIGSERIAL PRIMARY KEY,
                    booking_id BIGINT NOT NULL,
                    attendee_name VARCHAR(255) NOT NULL,
                    ticket_code VARCHAR(255) NOT NULL UNIQUE,
                    status VARCHAR(50) NOT NULL,
                    issued_at TIMESTAMP NOT NULL,
                    metadata JSONB
                )
                """);
        jdbcTemplate.execute("TRUNCATE TABLE tickets, ticket_sales, booking_items, bookings RESTART IDENTITY CASCADE");
    }

    @Test
    void completeBookingUpdatesBookingAndCreatesPendingSale() throws Exception {
        Booking booking = bookingRepository.saveAndFlush(checkedInBooking());

        mockMvc.perform(put("/api/bookings/{id}/complete", booking.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalAmount").value(650.0));

        Map<String, Object> sale = jdbcTemplate.queryForMap(
                """
                SELECT booking_id, user_id, amount, method, status,
                       transaction_details ->> 'bookingTotalAmount' AS booking_total_amount
                FROM ticket_sales
                WHERE booking_id = ?
                """,
                booking.getId()
        );

        assertEquals(booking.getId(), ((Number) sale.get("booking_id")).longValue());
        assertEquals(44L, ((Number) sale.get("user_id")).longValue());
        assertEquals(650.0, ((Number) sale.get("amount")).doubleValue());
        assertEquals("DEBIT_CARD", sale.get("method"));
        assertEquals("PENDING", sale.get("status"));
        assertEquals("650.0", sale.get("booking_total_amount"));
    }

    @Test
    void completeBookingRejectsPendingBookingAndDoesNotCreateSale() throws Exception {
        Booking booking = pendingBooking();
        booking = bookingRepository.saveAndFlush(booking);

        mockMvc.perform(put("/api/bookings/{id}/complete", booking.getId()))
                .andExpect(status().isBadRequest());

        Long saleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_sales WHERE booking_id = ?",
                Long.class,
                booking.getId()
        );
        assertEquals(0L, saleCount);
    }

    @Test
    void cancelBookingCancelsBookingAndAllValidTickets() throws Exception {
        Booking booking = confirmedBooking();
        booking = bookingRepository.saveAndFlush(booking);
        insertTicket(booking.getId(), "VALID", "TKT-1");
        insertTicket(booking.getId(), "VALID", "TKT-2");
        insertTicket(booking.getId(), "VALID", "TKT-3");
        insertTicket(booking.getId(), "USED", "TKT-4");

        mockMvc.perform(put("/api/bookings/{id}/cancel", booking.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, updatedBooking.getStatus());

        List<String> ticketStatuses = jdbcTemplate.queryForList(
                "SELECT status FROM tickets WHERE booking_id = ? ORDER BY ticket_code",
                String.class,
                booking.getId()
        );
        assertEquals(List.of("CANCELLED", "CANCELLED", "CANCELLED", "USED"), ticketStatuses);
    }

    @Test
    void cancelBookingRejectsCompletedBooking() throws Exception {
        Booking booking = bookingRepository.saveAndFlush(completedBooking());

        mockMvc.perform(put("/api/bookings/{id}/cancel", booking.getId()))
                .andExpect(status().isBadRequest());

        Booking unchangedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.COMPLETED, unchangedBooking.getStatus());
    }

    @Test
    void cancelBookingRejectsCheckedInBooking() throws Exception {
        Booking booking = bookingRepository.saveAndFlush(checkedInBooking());

        mockMvc.perform(put("/api/bookings/{id}/cancel", booking.getId()))
                .andExpect(status().isBadRequest());

        Booking unchangedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.CHECKED_IN, unchangedBooking.getStatus());
    }

    private Booking checkedInBooking() {
        Booking booking = new Booking();
        booking.setUserId(44L);
        booking.setEventId(88L);
        booking.setContactEmail("buyer@example.com");
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setMetadata(new LinkedHashMap<>(Map.of("paymentMethod", "debit_card")));
        booking.addBookingItem(bookingItem(1, 2, 100.0, BookingItemStatus.RESERVED));
        booking.addBookingItem(bookingItem(2, 1, 250.0, BookingItemStatus.RESERVED));
        booking.addBookingItem(bookingItem(3, 4, 50.0, BookingItemStatus.RESERVED));
        return booking;
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setUserId(44L);
        booking.setEventId(88L);
        booking.setContactEmail("buyer@example.com");
        booking.setStatus(BookingStatus.PENDING);
        booking.addBookingItem(bookingItem(1, 1, 100.0, BookingItemStatus.RESERVED));
        return booking;
    }

    private Booking confirmedBooking() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setEventId(99L);
        return booking;
    }

    private Booking completedBooking() {
        Booking booking = confirmedBooking();
        booking.setStatus(BookingStatus.COMPLETED);
        return booking;
    }

    private void insertTicket(Long bookingId, String status, String ticketCode) {
        jdbcTemplate.update(
                """
                INSERT INTO tickets (booking_id, attendee_name, ticket_code, status, issued_at, metadata)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                """,
                bookingId,
                "Attendee " + ticketCode,
                ticketCode,
                status,
                LocalDateTime.of(2026, 3, 30, 10, 0),
                "{}"
        );
    }

    private BookingItem bookingItem(int eventOrder, int quantity, double unitPrice, BookingItemStatus status) {
        BookingItem item = new BookingItem();
        item.setEventOrder(eventOrder);
        item.setSessionId((long) eventOrder);
        item.setSessionTitle("Session " + eventOrder);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setStatus(status);
        item.setMetadata(new LinkedHashMap<>());
        return item;
    }
}
