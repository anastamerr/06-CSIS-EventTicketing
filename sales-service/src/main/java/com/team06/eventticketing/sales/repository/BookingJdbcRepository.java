package com.team06.eventticketing.sales.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<BookingPaymentRow> findByIdForUpdate(Long bookingId) {
        return jdbcTemplate.query(
                """
                SELECT id, status, total_amount
                FROM bookings
                WHERE id = ?
                FOR UPDATE
                """,
                (resultSet, rowNum) -> mapRow(resultSet),
                bookingId
        ).stream().findFirst();
    }

    private BookingPaymentRow mapRow(ResultSet resultSet) throws SQLException {
        return new BookingPaymentRow(
                resultSet.getLong("id"),
                resultSet.getString("status"),
                resultSet.getObject("total_amount") == null ? null : resultSet.getDouble("total_amount")
        );
    }

    public record BookingPaymentRow(Long id, String status, Double totalAmount) {
    }
}
