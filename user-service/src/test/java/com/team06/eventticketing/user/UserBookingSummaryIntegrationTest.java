package com.team06.eventticketing.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.model.UserRole;
import com.team06.eventticketing.user.repository.UserRepository;
import java.math.BigDecimal;
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
@Testcontainers
class UserBookingSummaryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    total_amount NUMERIC(19, 4)
                )
                """);
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM favorite_venues");
        userRepository.deleteAll();
    }

    @Test
    void getBookingSummaryReturnsAggregatesForMixedStatuses() throws Exception {
        User user = createUser("Mariam", "mariam@example.com", "01000000001");
        insertBooking(user.getId(), "COMPLETED", "300.00");
        insertBooking(user.getId(), "COMPLETED", "500.00");
        insertBooking(user.getId(), "COMPLETED", "700.00");
        insertBooking(user.getId(), "CANCELLED", "250.00");
        insertBooking(user.getId(), "PENDING", "125.00");

        mockMvc.perform(get("/api/users/{id}/booking-summary", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.name").value("Mariam"))
                .andExpect(jsonPath("$.totalBookings").value(5))
                .andExpect(jsonPath("$.completedBookings").value(3))
                .andExpect(jsonPath("$.cancelledBookings").value(1))
                .andExpect(jsonPath("$.totalSpent").value(1500.0))
                .andExpect(jsonPath("$.averageBookingAmount").value(new BigDecimal("500.0000000000000000")));
    }

    @Test
    void getBookingSummaryReturnsZerosForUserWithoutBookings() throws Exception {
        User user = createUser("Layla", "layla@example.com", "01000000002");

        mockMvc.perform(get("/api/users/{id}/booking-summary", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.name").value("Layla"))
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.completedBookings").value(0))
                .andExpect(jsonPath("$.cancelledBookings").value(0))
                .andExpect(jsonPath("$.totalSpent").value(0))
                .andExpect(jsonPath("$.averageBookingAmount").value(0));
    }

    @Test
    void getBookingSummaryReturnsNotFoundForMissingUser() throws Exception {
        mockMvc.perform(get("/api/users/{id}/booking-summary", 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBookingSummaryHandlesDecimalAmounts() throws Exception {
        User user = createUser("Youssef", "youssef@example.com", "01000000003");
        insertBooking(user.getId(), "COMPLETED", "100.10");
        insertBooking(user.getId(), "COMPLETED", "200.20");

        mockMvc.perform(get("/api/users/{id}/booking-summary", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(2))
                .andExpect(jsonPath("$.completedBookings").value(2))
                .andExpect(jsonPath("$.cancelledBookings").value(0))
                .andExpect(jsonPath("$.totalSpent").value(300.3))
                .andExpect(jsonPath("$.averageBookingAmount").value(new BigDecimal("150.1500000000000000")));
    }

    @Test
    void getBookingSummaryTreatsNullCompletedAmountsAsZero() throws Exception {
        User user = createUser("Omar", "omar@example.com", "01000000004");
        insertBooking(user.getId(), "COMPLETED", null);
        insertBooking(user.getId(), "COMPLETED", "250.00");
        insertBooking(user.getId(), "CANCELLED", "999.00");

        mockMvc.perform(get("/api/users/{id}/booking-summary", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(3))
                .andExpect(jsonPath("$.completedBookings").value(2))
                .andExpect(jsonPath("$.cancelledBookings").value(1))
                .andExpect(jsonPath("$.totalSpent").value(250.0))
                .andExpect(jsonPath("$.averageBookingAmount").value(new BigDecimal("125.0000000000000000")));
    }

    private User createUser(String name, String email, String phone) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("secret");
        user.setPhone(phone);
        user.setRole(UserRole.ATTENDEE);
        user.setPreferences(Map.of());
        return userRepository.saveAndFlush(user);
    }

    private void insertBooking(Long userId, String status, String totalAmount) {
        BigDecimal amount = totalAmount == null ? null : new BigDecimal(totalAmount);
        jdbcTemplate.update(
                "INSERT INTO bookings (user_id, status, total_amount) VALUES (?, ?, ?)",
                userId,
                status,
                amount);
    }
}
