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


    private SaleEventDateRow mapSaleEventDateRow(ResultSet resultSet) throws SQLException {
        Object eventIdValue = resultSet.getObject("event_id");
        java.sql.Timestamp eventDateValue = resultSet.getTimestamp("event_date");

        return new SaleEventDateRow(
                eventIdValue == null ? null : ((Number) eventIdValue).longValue(),
                eventDateValue == null ? null : eventDateValue.toLocalDateTime()
        );
    }


    public Optional<SaleEventDateRow> findEventDateByTicketSaleId(Long saleId) {
        return jdbcTemplate.query(
                """
                SELECT b.event_id, e.event_date
                FROM ticket_sales ts
                JOIN bookings b ON b.id = ts.booking_id
                LEFT JOIN events e ON e.id = b.event_id
                WHERE ts.id = ?
                """,
                (resultSet, rowNum) -> mapSaleEventDateRow(resultSet),
                saleId
        ).stream().findFirst();
    }


    public record SaleEventDateRow(Long eventId, java.time.LocalDateTime eventDate) {
    }


}
