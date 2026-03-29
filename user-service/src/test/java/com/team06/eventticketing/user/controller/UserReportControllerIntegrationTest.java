package com.team06.eventticketing.user.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.model.UserRole;
import com.team06.eventticketing.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class UserReportControllerIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    total_amount DOUBLE PRECISION,
                    booking_date TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("TRUNCATE TABLE bookings, favorite_venues, users RESTART IDENTITY CASCADE");
    }

    @Test
    void getTopAttendeesReturnsHighestSpendersWithinRange() throws Exception {
        User userA = saveUser("User A", "usera@example.com", "01000000001");
        User userB = saveUser("User B", "userb@example.com", "01000000002");
        User userC = saveUser("User C", "userc@example.com", "01000000003");

        insertBooking(userA.getId(), "COMPLETED", 1_200.0, LocalDateTime.of(2026, 3, 5, 10, 0));
        insertBooking(userA.getId(), "COMPLETED", 800.0, LocalDateTime.of(2026, 3, 22, 10, 0));
        insertBooking(userB.getId(), "COMPLETED", 3_000.0, LocalDateTime.of(2026, 3, 7, 9, 0));
        insertBooking(userB.getId(), "COMPLETED", 2_000.0, LocalDateTime.of(2026, 3, 28, 18, 30));
        insertBooking(userC.getId(), "COMPLETED", 800.0, LocalDateTime.of(2026, 3, 17, 14, 45));

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].userId").value(userB.getId()))
                .andExpect(jsonPath("$[0].name").value("User B"))
                .andExpect(jsonPath("$[0].totalSpent").value(new BigDecimal("5000.0")))
                .andExpect(jsonPath("$[0].bookingCount").value(2))
                .andExpect(jsonPath("$[1].userId").value(userA.getId()))
                .andExpect(jsonPath("$[1].name").value("User A"))
                .andExpect(jsonPath("$[1].totalSpent").value(new BigDecimal("2000.0")))
                .andExpect(jsonPath("$[1].bookingCount").value(2));
    }

    @Test
    void getTopAttendeesReturnsEmptyListWhenNoBookingsQualify() throws Exception {
        saveUser("User A", "usera@example.com", "01000000001");

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2027-01-01")
                        .param("endDate", "2027-01-31")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getTopAttendeesRejectsStartDateAfterEndDate() throws Exception {
        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-31")
                        .param("endDate", "2026-03-01")
                        .param("limit", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTopAttendeesIncludesStartAndEndDateBoundaries() throws Exception {
        User boundaryUser = saveUser("Boundary User", "boundary@example.com", "01000000004");
        User outsideUser = saveUser("Outside User", "outside@example.com", "01000000005");

        insertBooking(boundaryUser.getId(), "COMPLETED", 300.0, LocalDateTime.of(2026, 3, 1, 0, 0));
        insertBooking(boundaryUser.getId(), "COMPLETED", 700.0, LocalDateTime.of(2026, 3, 31, 23, 59, 59));
        insertBooking(outsideUser.getId(), "COMPLETED", 5_000.0, LocalDateTime.of(2026, 4, 1, 0, 0));

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(boundaryUser.getId()))
                .andExpect(jsonPath("$[0].totalSpent").value(new BigDecimal("1000.0")))
                .andExpect(jsonPath("$[0].bookingCount").value(2));
    }

    @Test
    void getTopAttendeesRespectsLimitParameter() throws Exception {
        User userA = saveUser("User A", "usera@example.com", "01000000001");
        User userB = saveUser("User B", "userb@example.com", "01000000002");

        insertBooking(userA.getId(), "COMPLETED", 1_000.0, LocalDateTime.of(2026, 3, 10, 12, 0));
        insertBooking(userB.getId(), "COMPLETED", 2_000.0, LocalDateTime.of(2026, 3, 11, 12, 0));

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(userB.getId()));
    }

    @Test
    void getTopAttendeesUsesDeterministicTieBreakers() throws Exception {
        User alpha = saveUser("Alpha", "alpha@example.com", "01000000006");
        User beta = saveUser("Beta", "beta@example.com", "01000000007");
        User gamma = saveUser("Gamma", "gamma@example.com", "01000000008");

        insertBooking(alpha.getId(), "COMPLETED", 1_000.0, LocalDateTime.of(2026, 3, 10, 10, 0));
        insertBooking(beta.getId(), "COMPLETED", 600.0, LocalDateTime.of(2026, 3, 10, 11, 0));
        insertBooking(beta.getId(), "COMPLETED", 400.0, LocalDateTime.of(2026, 3, 10, 12, 0));
        insertBooking(gamma.getId(), "COMPLETED", 1_000.0, LocalDateTime.of(2026, 3, 10, 13, 0));

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(beta.getId()))
                .andExpect(jsonPath("$[1].userId").value(alpha.getId()))
                .andExpect(jsonPath("$[2].userId").value(gamma.getId()));
    }

    @Test
    void getTopAttendeesIgnoresNonCompletedBookingsInRange() throws Exception {
        User mixedUser = saveUser("Mixed User", "mixed@example.com", "01000000009");
        User completedUser = saveUser("Completed User", "completed@example.com", "01000000010");

        insertBooking(mixedUser.getId(), "COMPLETED", 100.0, LocalDateTime.of(2026, 3, 15, 10, 0));
        insertBooking(mixedUser.getId(), "PENDING", 10_000.0, LocalDateTime.of(2026, 3, 15, 11, 0));
        insertBooking(completedUser.getId(), "COMPLETED", 500.0, LocalDateTime.of(2026, 3, 15, 12, 0));

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(completedUser.getId()))
                .andExpect(jsonPath("$[1].userId").value(mixedUser.getId()))
                .andExpect(jsonPath("$[1].totalSpent").value(new BigDecimal("100.0")))
                .andExpect(jsonPath("$[1].bookingCount").value(1));
    }

    @Test
    void getTopAttendeesIgnoresCompletedBookingsOutsideRange() throws Exception {
        User inRangeUser = saveUser("In Range", "inrange@example.com", "01000000011");
        User mixedUser = saveUser("Mixed User", "mixed-range@example.com", "01000000012");

        insertBooking(inRangeUser.getId(), "COMPLETED", 500.0, LocalDateTime.of(2026, 3, 20, 10, 0));
        insertBooking(mixedUser.getId(), "COMPLETED", 100.0, LocalDateTime.of(2026, 3, 21, 10, 0));
        insertBooking(mixedUser.getId(), "COMPLETED", 10_000.0, LocalDateTime.of(2026, 4, 1, 10, 0));

        mockMvc.perform(get("/api/users/reports/top-attendees")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(inRangeUser.getId()))
                .andExpect(jsonPath("$[1].userId").value(mixedUser.getId()))
                .andExpect(jsonPath("$[1].totalSpent").value(new BigDecimal("100.0")))
                .andExpect(jsonPath("$[1].bookingCount").value(1));
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

    private void insertBooking(Long userId, String status, double totalAmount, LocalDateTime bookingDate) {
        jdbcTemplate.update(
                "INSERT INTO bookings (user_id, status, total_amount, booking_date) VALUES (?, ?, ?, ?)",
                userId, status, totalAmount, bookingDate);
    }
}
