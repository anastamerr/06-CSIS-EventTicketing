package com.team06.eventticketing.booking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.team06.eventticketing.booking.dto.AddBookingItemsRequest;
import com.team06.eventticketing.booking.dto.BookingItemRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import java.util.ArrayList;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void addItemsToPendingBookingCreatesItemsWithCorrectEventOrder() throws Exception {
        Booking booking = new Booking();
        booking.setUserId(1L);
        booking.setEventId(1L);
        booking.setContactEmail("test@example.com");
        booking.setStatus(BookingStatus.PENDING);
        booking = bookingRepository.saveAndFlush(booking);

        AddBookingItemsRequest request = new AddBookingItemsRequest();
        List<BookingItemRequest> items = new ArrayList<>();

        BookingItemRequest item1 = new BookingItemRequest();
        item1.setSessionId(101L);
        item1.setSessionTitle("Session 101");
        item1.setQuantity(2);
        item1.setUnitPrice(50.0);
        items.add(item1);

        BookingItemRequest item2 = new BookingItemRequest();
        item2.setSessionId(102L);
        item2.setSessionTitle("Session 102");
        item2.setQuantity(1);
        item2.setUnitPrice(75.0);
        items.add(item2);

        request.setItems(items);

        mockMvc.perform(post("/api/bookings/{bookingId}/items", booking.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingItems.length()").value(2))
                .andExpect(jsonPath("$.bookingItems[0].eventOrder").value(1))
                .andExpect(jsonPath("$.bookingItems[0].sessionId").value(101))
                .andExpect(jsonPath("$.bookingItems[0].sessionTitle").value("Session 101"))
                .andExpect(jsonPath("$.bookingItems[0].status").value("RESERVED"))
                .andExpect(jsonPath("$.bookingItems[1].eventOrder").value(2))
                .andExpect(jsonPath("$.bookingItems[1].sessionId").value(102))
                .andExpect(jsonPath("$.bookingItems[1].sessionTitle").value("Session 102"))
                .andExpect(jsonPath("$.bookingItems[1].status").value("RESERVED"));
    }

    @Test
    void addMoreItemsContinuesEventOrder() throws Exception {
        Booking booking = new Booking();
        booking.setUserId(1L);
        booking.setEventId(1L);
        booking.setContactEmail("test@example.com");
        booking.setStatus(BookingStatus.PENDING);
        booking.addBookingItem(bookingItem(1, 1, 100.0, BookingItemStatus.RESERVED));
        booking.addBookingItem(bookingItem(2, 1, 100.0, BookingItemStatus.RESERVED));
        booking = bookingRepository.saveAndFlush(booking);

        AddBookingItemsRequest request = new AddBookingItemsRequest();
        List<BookingItemRequest> items = new ArrayList<>();

        BookingItemRequest item = new BookingItemRequest();
        item.setSessionId(103L);
        item.setSessionTitle("Session 103");
        item.setQuantity(3);
        item.setUnitPrice(25.0);
        items.add(item);

        request.setItems(items);

        mockMvc.perform(post("/api/bookings/{bookingId}/items", booking.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingItems.length()").value(3))
                .andExpect(jsonPath("$.bookingItems[2].eventOrder").value(3))
                .andExpect(jsonPath("$.bookingItems[2].sessionId").value(103));
    }

    @Test
    void addItemsToCompletedBookingReturns400() throws Exception {
        Booking booking = new Booking();
        booking.setUserId(1L);
        booking.setEventId(1L);
        booking.setContactEmail("test@example.com");
        booking.setStatus(BookingStatus.COMPLETED);
        booking = bookingRepository.saveAndFlush(booking);

        AddBookingItemsRequest request = new AddBookingItemsRequest();
        List<BookingItemRequest> items = new ArrayList<>();

        BookingItemRequest item = new BookingItemRequest();
        item.setSessionId(101L);
        item.setSessionTitle("Session 101");
        item.setQuantity(1);
        item.setUnitPrice(50.0);
        items.add(item);

        request.setItems(items);

        mockMvc.perform(post("/api/bookings/{bookingId}/items", booking.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItemWithMissingSessionTitleReturns400() throws Exception {
        Booking booking = new Booking();
        booking.setUserId(1L);
        booking.setEventId(1L);
        booking.setContactEmail("test@example.com");
        booking.setStatus(BookingStatus.PENDING);
        booking = bookingRepository.saveAndFlush(booking);

        AddBookingItemsRequest request = new AddBookingItemsRequest();
        List<BookingItemRequest> items = new ArrayList<>();

        BookingItemRequest item = new BookingItemRequest();
        item.setSessionId(101L);
        // sessionTitle is missing
        item.setQuantity(1);
        item.setUnitPrice(50.0);
        items.add(item);

        request.setItems(items);

        mockMvc.perform(post("/api/bookings/{bookingId}/items", booking.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItemsToNonExistentBookingReturns404() throws Exception {
        AddBookingItemsRequest request = new AddBookingItemsRequest();
        List<BookingItemRequest> items = new ArrayList<>();

        BookingItemRequest item = new BookingItemRequest();
        item.setSessionId(101L);
        item.setSessionTitle("Session 101");
        item.setQuantity(1);
        item.setUnitPrice(50.0);
        items.add(item);

        request.setItems(items);

        mockMvc.perform(post("/api/bookings/{bookingId}/items", 99999L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
