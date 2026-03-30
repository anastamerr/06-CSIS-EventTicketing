package com.team06.eventticketing.user.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.model.UserRole;
import com.team06.eventticketing.user.repository.UserRepository;
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
class UserFavoriteCategoryControllerIntegrationTest {

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
                    booking_date TIMESTAMP
                )
                """);
        jdbcTemplate.execute("TRUNCATE TABLE bookings, favorite_venues, users RESTART IDENTITY CASCADE");
    }

    @Test
    void getUsersByFavoriteCategoryReturnsOnlyUsersMeetingMinimumCompletedBookings() throws Exception {
        User userA = saveUser("User A", "usera@example.com", "01000000001", "CONCERT");
        User userB = saveUser("User B", "userb@example.com", "01000000002", "CONCERT");
        saveUser("User C", "userc@example.com", "01000000003", "SPORTS");

        insertBookings(userA.getId(), "COMPLETED", 5);
        insertBookings(userB.getId(), "COMPLETED", 2);
        insertBookings(userB.getId(), "PENDING", 4);

        mockMvc.perform(get("/api/users/preferences/category")
                        .param("category", "CONCERT")
                        .param("minBookings", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(userA.getId()))
                .andExpect(jsonPath("$[0].name").value("User A"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void getUsersByFavoriteCategoryIncludesAllUsersMeetingLowerMinimum() throws Exception {
        User userA = saveUser("User A", "usera@example.com", "01000000001", "CONCERT");
        User userB = saveUser("User B", "userb@example.com", "01000000002", "CONCERT");
        saveUser("User C", "userc@example.com", "01000000003", "SPORTS");

        insertBookings(userA.getId(), "COMPLETED", 5);
        insertBookings(userB.getId(), "COMPLETED", 2);

        mockMvc.perform(get("/api/users/preferences/category")
                        .param("category", "CONCERT")
                        .param("minBookings", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        userA.getId().intValue(),
                        userB.getId().intValue())));
    }

    @Test
    void getUsersByFavoriteCategoryRejectsBlankCategory() throws Exception {
        mockMvc.perform(get("/api/users/preferences/category")
                        .param("category", " ")
                        .param("minBookings", "1"))
                .andExpect(status().isBadRequest());
    }

    private User saveUser(String name, String email, String phone, String favoriteCategory) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("password-" + name);
        user.setPhone(phone);
        user.setRole(UserRole.ATTENDEE);
        user.setPreferences(Map.of("favoriteCategory", favoriteCategory));
        return userRepository.save(user);
    }

    private void insertBookings(Long userId, String status, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO bookings (user_id, status, total_amount, booking_date) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    userId, status, 100.0 + i);
        }
    }
}
