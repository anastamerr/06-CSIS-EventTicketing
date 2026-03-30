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
@Testcontainers(disabledWithoutDocker = true)
class UserBookingSummaryIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    total_amount NUMERIC(19, 4)
                )
                """);
        jdbcTemplate.execute("TRUNCATE TABLE bookings, favorite_venues, users RESTART IDENTITY CASCADE");
    }

    @Test
    void getBookingSummaryReturnsAggregatesForMixedStatuses() throws Exception {
        User user = saveUser("Mariam", "mariam@example.com", "01000000001");
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
        User user = saveUser("Layla", "layla@example.com", "01000000002");

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
        User user = saveUser("Youssef", "youssef@example.com", "01000000003");
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
        User user = saveUser("Omar", "omar@example.com", "01000000004");
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

    @Test
    void searchUsersByFiltersWorks() throws Exception {
        saveUser("Ahmed", "ahmed@example.com", "01000000005");
        User userB = saveUser("Sara", "sara@example.com", "01000000006");
        userB.setRole(UserRole.ADMIN);
        userRepository.save(userB);
        saveUser("Ahmed Ali", "ahmed.ali@example.com", "01000000007");

        mockMvc.perform(get("/api/users/search").param("name", "Ahmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/users/search").param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/users/search").param("name", "xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/users/search").param("name", "ahmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/users/search").param("email", "EXAMPLE.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    private User saveUser(String name, String email, String phone) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("password");
        user.setPhone(phone);
        user.setRole(UserRole.ATTENDEE);
        user.setPreferences(Map.of());
        return userRepository.save(user);
    }

    private void insertBooking(Long userId, String status, String totalAmount) {
        BigDecimal amount = totalAmount == null ? null : new BigDecimal(totalAmount);
        jdbcTemplate.update(
                "INSERT INTO bookings (user_id, status, total_amount) VALUES (?, ?, ?)",
                userId, status, amount);
    }
}
