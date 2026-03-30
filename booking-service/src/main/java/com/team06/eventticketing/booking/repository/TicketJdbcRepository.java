package com.team06.eventticketing.booking.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TicketJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public TicketJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int cancelValidTicketsForBooking(Long bookingId) {
        return jdbcTemplate.update(
                """
                UPDATE tickets
                SET status = 'CANCELLED'
                WHERE booking_id = ?
                  AND status = 'VALID'
                """,
                bookingId
        );
    }
}
