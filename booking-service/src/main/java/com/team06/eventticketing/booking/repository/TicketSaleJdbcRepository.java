package com.team06.eventticketing.booking.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TicketSaleJdbcRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public TicketSaleJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByBookingId(Long bookingId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM ticket_sales WHERE booking_id = ?)",
                Boolean.class,
                bookingId
        );
        return Boolean.TRUE.equals(exists);
    }

    public void createCompletedSale(
            Long bookingId,
            Long userId,
            double amount,
            String method,
            Map<String, Object> transactionDetails
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO ticket_sales (booking_id, user_id, amount, method, status, transaction_details, created_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CURRENT_TIMESTAMP)
                """,
                bookingId,
                userId,
                amount,
                method,
                "COMPLETED",
                serializeTransactionDetails(transactionDetails)
        );
    }

    private String serializeTransactionDetails(Map<String, Object> transactionDetails) {
        try {
            return OBJECT_MAPPER.writeValueAsString(
                    transactionDetails == null ? new LinkedHashMap<>() : new LinkedHashMap<>(transactionDetails)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize ticket sale transaction details", exception);
        }
    }
}
