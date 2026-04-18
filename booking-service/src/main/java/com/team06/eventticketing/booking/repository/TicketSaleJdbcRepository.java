package com.team06.eventticketing.booking.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.SingleColumnRowMapper;
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

    public void createPendingSale(
            Long bookingId,
            Long userId,
            double amount,
            String method,
            Map<String, Object> transactionDetails
    ) {
        String methodExpression = resolveColumnValueExpression("ticket_sales", "method");
        String statusExpression = resolveColumnValueExpression("ticket_sales", "status");
        jdbcTemplate.update(
                """
                INSERT INTO ticket_sales (booking_id, user_id, amount, method, status, transaction_details, created_at)
                VALUES (?, ?, ?, %s, %s, CAST(? AS jsonb), CURRENT_TIMESTAMP)
                """.formatted(methodExpression, statusExpression),
                bookingId,
                userId,
                amount,
                method,
                "PENDING",
                serializeTransactionDetails(transactionDetails)
        );
    }

    private String resolveColumnValueExpression(String tableName, String columnName) {
        String udtName = jdbcTemplate.query(
                """
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """,
                ps -> {
                    ps.setString(1, tableName);
                    ps.setString(2, columnName);
                },
                rs -> rs.next() ? SingleColumnRowMapper.newInstance(String.class).mapRow(rs, 0) : null
        );
        if (udtName == null || udtName.isBlank()
                || "varchar".equalsIgnoreCase(udtName)
                || "text".equalsIgnoreCase(udtName)) {
            return "?";
        }
        return "CAST(? AS " + udtName + ")";
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
