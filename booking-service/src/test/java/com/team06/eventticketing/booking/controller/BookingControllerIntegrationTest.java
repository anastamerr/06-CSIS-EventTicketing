package com.team06.eventticketing.booking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.booking.dto.BookingItemRequest;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id BIGSERIAL PRIMARY KEY,
                    status VARCHAR(50) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS event_sessions (
                    id BIGSERIAL PRIMARY KEY,
                    event_id BIGINT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    speaker VARCHAR(255),
                    start_time TIMESTAMP NOT NULL,
                    end_time TIMESTAMP NOT NULL,
                    capacity INTEGER NOT NULL,
                    verified BOOLEAN NOT NULL DEFAULT FALSE,
                    metadata JSONB,
                    created_at TIMESTAMP NOT NULL
                )
                """);
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
        jdbcTemplate.execute("TRUNCATE TABLE tickets, event_sessions, ticket_sales, booking_items, bookings, events RESTART IDENTITY CASCADE");
    }

    @Test
    void estimateBookingCostReturnsExpectedDtoAndDoesNotCreateBooking() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO event_sessions (event_id, title, start_time, end_time, capacity, created_at) VALUES (?, ?, NOW(), NOW(), ?, NOW())",
                77L, "Session 1", 400);
        jdbcTemplate.update(
                "INSERT INTO event_sessions (event_id, title, start_time, end_time, capacity, created_at) VALUES (?, ?, NOW(), NOW(), ?, NOW())",
                77L, "Session 2", 600);

        mockMvc.perform(post("/api/bookings/estimate")
                        .contentType("application/json")
                        .content("""
                                {"eventId":77,"ticketCount":2,"ticketTier":"VIP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCost").value(250.0))
                .andExpect(jsonPath("$.serviceFee").value(37.5))
                .andExpect(jsonPath("$.demandMultiplier").value(1.0))
                .andExpect(jsonPath("$.estimatedTotal").value(287.5));

        Long bookingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bookings", Long.class);
        assertEquals(0L, bookingCount);
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
    void confirmBookingAssignsUpcomingEventToPendingBooking() throws Exception {
        Long eventId = insertEvent("UPCOMING");
        Booking booking = bookingRepository.saveAndFlush(pendingBooking());

        mockMvc.perform(put("/api/bookings/{bookingId}/confirm", booking.getId())
                        .queryParam("eventId", eventId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.confirmedAt").exists());

        Booking confirmedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, confirmedBooking.getStatus());
        assertEquals(eventId, confirmedBooking.getEventId());
        assertNotNull(confirmedBooking.getConfirmedAt());
    }

    @Test
    void confirmBookingRejectsAlreadyConfirmedBooking() throws Exception {
        Long eventId = insertEvent("UPCOMING");
        Booking booking = bookingRepository.saveAndFlush(confirmedBooking());

        mockMvc.perform(put("/api/bookings/{bookingId}/confirm", booking.getId())
                        .queryParam("eventId", eventId.toString()))
                .andExpect(status().isBadRequest());

        Booking unchangedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, unchangedBooking.getStatus());
    }

    @Test
    void confirmBookingRejectsCancelledEvent() throws Exception {
        Long eventId = insertEvent("CANCELLED");
        Booking booking = bookingRepository.saveAndFlush(pendingBooking());

        mockMvc.perform(put("/api/bookings/{bookingId}/confirm", booking.getId())
                        .queryParam("eventId", eventId.toString()))
                .andExpect(status().isBadRequest());

        Booking unchangedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.PENDING, unchangedBooking.getStatus());
        assertEquals(null, unchangedBooking.getConfirmedAt());
    }

    @Test
    void confirmBookingRejectsMissingBooking() throws Exception {
        Long eventId = insertEvent("UPCOMING");

        mockMvc.perform(put("/api/bookings/{bookingId}/confirm", 99999L)
                        .queryParam("eventId", eventId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmBookingRejectsMissingEvent() throws Exception {
        Booking booking = bookingRepository.saveAndFlush(pendingBooking());

        mockMvc.perform(put("/api/bookings/{bookingId}/confirm", booking.getId())
                        .queryParam("eventId", "99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchBookingsReturnsCompletedMarchBookingsMostRecentFirst() throws Exception {
        Booking firstMarchCompleted = bookingRepository.saveAndFlush(
                bookingWithDate(BookingStatus.COMPLETED, LocalDateTime.of(2026, 3, 5, 10, 0))
        );
        bookingRepository.saveAndFlush(bookingWithDate(BookingStatus.PENDING, LocalDateTime.of(2026, 3, 10, 10, 0)));
        Booking secondMarchCompleted = bookingRepository.saveAndFlush(
                bookingWithDate(BookingStatus.COMPLETED, LocalDateTime.of(2026, 3, 25, 10, 0))
        );
        bookingRepository.saveAndFlush(bookingWithDate(BookingStatus.COMPLETED, LocalDateTime.of(2026, 2, 20, 10, 0)));
        bookingRepository.saveAndFlush(bookingWithDate(BookingStatus.COMPLETED, LocalDateTime.of(2026, 2, 10, 10, 0)));

        mockMvc.perform(get("/api/bookings/search")
                        .queryParam("status", "COMPLETED")
                        .queryParam("startDate", "2026-03-01")
                        .queryParam("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondMarchCompleted.getId()))
                .andExpect(jsonPath("$[1].id").value(firstMarchCompleted.getId()));
    }

    @Test
    void searchBookingsReturnsAllMarchBookingsWhenStatusMissing() throws Exception {
        bookingRepository.saveAndFlush(bookingWithDate(BookingStatus.COMPLETED, LocalDateTime.of(2026, 3, 5, 10, 0)));
        Booking pendingMarchBooking = bookingRepository.saveAndFlush(
                bookingWithDate(BookingStatus.PENDING, LocalDateTime.of(2026, 3, 10, 10, 0))
        );
        Booking latestMarchBooking = bookingRepository.saveAndFlush(
                bookingWithDate(BookingStatus.COMPLETED, LocalDateTime.of(2026, 3, 25, 10, 0))
        );
        bookingRepository.saveAndFlush(bookingWithDate(BookingStatus.COMPLETED, LocalDateTime.of(2026, 2, 20, 10, 0)));
        bookingRepository.saveAndFlush(bookingWithDate(BookingStatus.COMPLETED, LocalDateTime.of(2026, 2, 10, 10, 0)));

        mockMvc.perform(get("/api/bookings/search")
                        .queryParam("startDate", "2026-03-01")
                        .queryParam("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(latestMarchBooking.getId()))
                .andExpect(jsonPath("$[1].id").value(pendingMarchBooking.getId()));
    }

    @Test
    void searchBookingsRequiresBothDates() throws Exception {
        mockMvc.perform(get("/api/bookings/search")
                        .queryParam("status", "COMPLETED")
                        .queryParam("startDate", "2026-03-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBookingsByMetadataReturnsMatches() throws Exception {
        bookingRepository.saveAndFlush(bookingWithMetadata("VIP", LocalDateTime.of(2026, 3, 20, 10, 0)));
        bookingRepository.saveAndFlush(bookingWithMetadata("standard", LocalDateTime.of(2026, 3, 21, 10, 0)));
        Booking latestStandard = bookingRepository.saveAndFlush(
                bookingWithMetadata("standard", LocalDateTime.of(2026, 3, 22, 10, 0))
        );

        mockMvc.perform(get("/api/bookings/metadata/search")
                        .queryParam("key", "ticketTier")
                        .queryParam("value", "VIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].metadata.ticketTier").value("VIP"));

        mockMvc.perform(get("/api/bookings/metadata/search")
                        .queryParam("key", "ticketTier")
                        .queryParam("value", "standard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(latestStandard.getId()));
    }

    @Test
    void searchBookingsByMetadataRejectsBlankKey() throws Exception {
        mockMvc.perform(get("/api/bookings/metadata/search")
                        .queryParam("key", "")
                        .queryParam("value", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBookingsByMetadataRejectsBlankValue() throws Exception {
        mockMvc.perform(get("/api/bookings/metadata/search")
                        .queryParam("key", "ticketTier")
                        .queryParam("value", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBookingDetailsReturnsOrderedItemsAndCounts() throws Exception {
        Booking booking = pendingBooking();
        booking.setMetadata(new LinkedHashMap<>(Map.of("ticketTier", "VIP")));
        booking.setTotalAmount(425.0);
        booking.getBookingItems().clear();
        booking.addBookingItem(bookingItemWithMetadata(4, 1, 90.0, BookingItemStatus.RESERVED, "D"));
        booking.addBookingItem(bookingItemWithMetadata(2, 2, 80.0, BookingItemStatus.CONFIRMED, "B"));
        booking.addBookingItem(bookingItemWithMetadata(1, 1, 120.0, BookingItemStatus.CONFIRMED, "A"));
        booking.addBookingItem(bookingItemWithMetadata(3, 3, 25.0, BookingItemStatus.RESERVED, "C"));
        booking = bookingRepository.saveAndFlush(booking);

        mockMvc.perform(get("/api/bookings/{bookingId}/details", booking.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(booking.getId()))
                .andExpect(jsonPath("$.userId").value(44L))
                .andExpect(jsonPath("$.eventId").value(88L))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(425.0))
                .andExpect(jsonPath("$.metadata.ticketTier").value("VIP"))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.confirmedItems").value(2))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].eventOrder").value(1))
                .andExpect(jsonPath("$.items[0].sessionTitle").value("Session 1"))
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[1].eventOrder").value(2))
                .andExpect(jsonPath("$.items[2].eventOrder").value(3))
                .andExpect(jsonPath("$.items[3].eventOrder").value(4));
    }

    @Test
    void getBookingDetailsReturnsEmptyItemsWhenBookingHasNoItems() throws Exception {
        Booking booking = pendingBooking();
        booking.getBookingItems().clear();
        booking.setMetadata(new LinkedHashMap<>(Map.of("ticketTier", "standard")));
        booking = bookingRepository.saveAndFlush(booking);

        mockMvc.perform(get("/api/bookings/{bookingId}/details", booking.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(booking.getId()))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.confirmedItems").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void getBookingDetailsReturnsNotFoundForMissingBooking() throws Exception {
        mockMvc.perform(get("/api/bookings/{bookingId}/details", 99999L))
                .andExpect(status().isNotFound());
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

    @Test
    void addItemsToBookingCreatesReservedItemsWithSequentialEventOrder() throws Exception {
        Booking booking = pendingBooking();
        booking.getBookingItems().clear();
        booking = bookingRepository.saveAndFlush(booking);

        List<BookingItemRequest> request = List.of(
                bookingItemRequest(101L, "Session 101", 2, 50.0),
                bookingItemRequest(102L, "Session 102", 1, 75.0)
        );

        mockMvc.perform(post("/api/bookings/{bookingId}/items", booking.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingItems.length()").value(2))
                .andExpect(jsonPath("$.bookingItems[0].eventOrder").value(1))
                .andExpect(jsonPath("$.bookingItems[0].status").value("RESERVED"))
                .andExpect(jsonPath("$.bookingItems[1].eventOrder").value(2))
                .andExpect(jsonPath("$.bookingItems[1].status").value("RESERVED"));
    }

    @Test
    void addItemsToBookingContinuesExistingEventOrder() throws Exception {
        Booking booking = pendingBooking();
        booking = bookingRepository.saveAndFlush(booking);

        List<BookingItemRequest> request = List.of(
                bookingItemRequest(104L, "Session 104", 1, 80.0)
        );

        mockMvc.perform(post("/api/bookings/{bookingId}/items", booking.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingItems.length()").value(2))
                .andExpect(jsonPath("$.bookingItems[1].eventOrder").value(2));
    }

    @Test
    void addItemsToCompletedBookingReturnsBadRequest() throws Exception {
        Booking booking = checkedInBooking();
        booking.setStatus(BookingStatus.COMPLETED);
        booking = bookingRepository.saveAndFlush(booking);

        List<BookingItemRequest> request = List.of(
                bookingItemRequest(101L, "Session 101", 1, 50.0)
        );

        mockMvc.perform(post("/api/bookings/{bookingId}/items", booking.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItemsToBookingRejectsMissingSessionTitle() throws Exception {
        Booking booking = pendingBooking();
        booking.getBookingItems().clear();
        booking = bookingRepository.saveAndFlush(booking);

        BookingItemRequest invalidItem = new BookingItemRequest();
        invalidItem.setSessionId(101L);
        invalidItem.setQuantity(1);
        invalidItem.setUnitPrice(50.0);
        List<BookingItemRequest> request = List.of(invalidItem);

        mockMvc.perform(post("/api/bookings/{bookingId}/items", booking.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItemsToMissingBookingReturnsNotFound() throws Exception {
        List<BookingItemRequest> request = List.of(
                bookingItemRequest(101L, "Session 101", 1, 50.0)
        );

        mockMvc.perform(post("/api/bookings/{bookingId}/items", 99999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
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

    private Booking bookingWithDate(BookingStatus status, LocalDateTime bookingDate) {
        Booking booking = pendingBooking();
        booking.setStatus(status);
        booking.setBookingDate(bookingDate);
        return booking;
    }

    private Booking bookingWithMetadata(String ticketTier, LocalDateTime bookingDate) {
        Booking booking = pendingBooking();
        booking.setBookingDate(bookingDate);
        booking.setMetadata(new LinkedHashMap<>(Map.of("ticketTier", ticketTier)));
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

    private Long insertEvent(String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO events (status) VALUES (?) RETURNING id",
                Long.class,
                status
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

    private BookingItem bookingItemWithMetadata(
            int eventOrder,
            int quantity,
            double unitPrice,
            BookingItemStatus status,
            String section
    ) {
        BookingItem item = bookingItem(eventOrder, quantity, unitPrice, status);
        item.setMetadata(new LinkedHashMap<>(Map.of("section", section)));
        return item;
    }

    private BookingItemRequest bookingItemRequest(Long sessionId, String sessionTitle, Integer quantity, Double unitPrice) {
        BookingItemRequest request = new BookingItemRequest();
        request.setSessionId(sessionId);
        request.setSessionTitle(sessionTitle);
        request.setQuantity(quantity);
        request.setUnitPrice(unitPrice);
        return request;
    }
}
