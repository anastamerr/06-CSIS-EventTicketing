package com.team06.eventticketing.sales.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleMethod;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import java.util.LinkedHashMap;
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
class UserSaleSummaryControllerIntegrationTest {

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
    private TicketSaleRepository ticketSaleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ticketSaleRepository.deleteAll();
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id BIGINT PRIMARY KEY)");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void getUserSaleSummaryAggregatesCompletedSalesByMethod() throws Exception {
        jdbcTemplate.update("INSERT INTO users(id) VALUES (?)", 20L);

        ticketSaleRepository.saveAndFlush(newTicketSale(10L, 20L, 300.0, TicketSaleMethod.CREDIT_CARD, TicketSaleStatus.COMPLETED));
        ticketSaleRepository.saveAndFlush(newTicketSale(11L, 20L, 500.0, TicketSaleMethod.CREDIT_CARD, TicketSaleStatus.COMPLETED));
        ticketSaleRepository.saveAndFlush(newTicketSale(12L, 20L, 200.0, TicketSaleMethod.DEBIT_CARD, TicketSaleStatus.COMPLETED));
        ticketSaleRepository.saveAndFlush(newTicketSale(13L, 20L, 150.0, TicketSaleMethod.WALLET, TicketSaleStatus.COMPLETED));
        ticketSaleRepository.saveAndFlush(newTicketSale(14L, 20L, 999.0, TicketSaleMethod.WALLET, TicketSaleStatus.PENDING));

        mockMvc.perform(get("/api/sales/user/{userId}/summary", 20L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(20))
                .andExpect(jsonPath("$.totalSales").value(4))
                .andExpect(jsonPath("$.totalAmount").value(1150.0))
                .andExpect(jsonPath("$.methodBreakdown.CREDIT_CARD").value(800.0))
                .andExpect(jsonPath("$.methodBreakdown.DEBIT_CARD").value(200.0))
                .andExpect(jsonPath("$.methodBreakdown.WALLET").value(150.0));
    }

    @Test
    void getUserSaleSummaryReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/sales/user/{userId}/summary", 404L))
                .andExpect(status().isNotFound());
    }

    private TicketSale newTicketSale(
            Long bookingId,
            Long userId,
            double amount,
            TicketSaleMethod method,
            TicketSaleStatus status
    ) {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setBookingId(bookingId);
        ticketSale.setUserId(userId);
        ticketSale.setAmount(amount);
        ticketSale.setMethod(method);
        ticketSale.setStatus(status);
        ticketSale.setTransactionDetails(new LinkedHashMap<>());
        return ticketSale;
    }
}
