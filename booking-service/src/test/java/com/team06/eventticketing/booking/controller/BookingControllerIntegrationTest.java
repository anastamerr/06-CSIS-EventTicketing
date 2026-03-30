package com.team06.eventticketing.booking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        jdbcTemplate.execute("TRUNCATE TABLE ticket_sales, booking_items, bookings RESTART IDENTITY CASCADE");
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
    void searchBookingsReturnsStatusMatchesMostRecentFirst() throws Exception {
        bookingRepository.saveAndFlush(bookingWithDate("march-older@example.com",
                BookingStatus.COMPLETED,
                LocalDateTime.of(2026, 3, 10, 10, 0)));
        bookingRepository.saveAndFlush(bookingWithDate("march-latest@example.com",
                BookingStatus.COMPLETED,
                LocalDateTime.of(2026, 3, 20, 12, 0)));
        bookingRepository.saveAndFlush(bookingWithDate("march-pending@example.com",
                BookingStatus.PENDING,
                LocalDateTime.of(2026, 3, 15, 9, 0)));
        bookingRepository.saveAndFlush(bookingWithDate("feb-one@example.com",
                BookingStatus.COMPLETED,
                LocalDateTime.of(2026, 2, 20, 12, 0)));
        bookingRepository.saveAndFlush(bookingWithDate("feb-two@example.com",
                BookingStatus.COMPLETED,
                LocalDateTime.of(2026, 2, 25, 12, 0)));

        mockMvc.perform(get("/api/bookings/search")
                        .param("status", "COMPLETED")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].contactEmail").value("march-latest@example.com"))
                .andExpect(jsonPath("$[1].contactEmail").value("march-older@example.com"));
    }

    @Test
    void searchBookingsWithoutStatusReturnsAllMatchesInDateRange() throws Exception {
        bookingRepository.saveAndFlush(bookingWithDate("march-older@example.com",
                BookingStatus.COMPLETED,
                LocalDateTime.of(2026, 3, 10, 10, 0)));
        bookingRepository.saveAndFlush(bookingWithDate("march-latest@example.com",
                BookingStatus.COMPLETED,
                LocalDateTime.of(2026, 3, 20, 12, 0)));
        bookingRepository.saveAndFlush(bookingWithDate("march-pending@example.com",
                BookingStatus.PENDING,
                LocalDateTime.of(2026, 3, 15, 9, 0)));
        bookingRepository.saveAndFlush(bookingWithDate("feb-one@example.com",
                BookingStatus.COMPLETED,
                LocalDateTime.of(2026, 2, 20, 12, 0)));
        bookingRepository.saveAndFlush(bookingWithDate("feb-two@example.com",
                BookingStatus.COMPLETED,
                LocalDateTime.of(2026, 2, 25, 12, 0)));

        mockMvc.perform(get("/api/bookings/search")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].contactEmail").value("march-latest@example.com"))
                .andExpect(jsonPath("$[1].contactEmail").value("march-pending@example.com"))
                .andExpect(jsonPath("$[2].contactEmail").value("march-older@example.com"));
    }

    @Test
    void searchBookingsRejectsInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/bookings/search")
                        .param("status", "COMPLETED")
                        .param("startDate", "2026-03-31")
                        .param("endDate", "2026-03-01"))
                .andExpect(status().isBadRequest());
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

    private Booking bookingWithDate(String email, BookingStatus status, LocalDateTime bookingDate) {
        Booking booking = new Booking();
        booking.setUserId(60L);
        booking.setEventId(77L);
        booking.setContactEmail(email);
        booking.setStatus(status);
        booking.setBookingDate(bookingDate);
        return booking;
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
