package com.team06.eventticketing.booking.repository;

import com.team06.eventticketing.booking.dto.BookingAttendanceData;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingAttendanceLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookingAttendanceLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<BookingAttendanceData> findAttendanceDataByBookingId(Long bookingId) {
        String sql = """
                SELECT
                    b.id AS booking_id,
                    b.status AS booking_status,
                    u.id AS user_id,
                    u.name AS user_name,
                    e.id AS event_id,
                    e.name AS event_name,
                    e.category AS event_category
                FROM bookings b
                LEFT JOIN users u ON b.user_id = u.id
                LEFT JOIN events e ON b.event_id = e.id
                WHERE b.id = ?
                """;

        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new BookingAttendanceData(
                    rs.getLong("booking_id"),
                    rs.getString("booking_status"),
                    rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                    rs.getString("user_name"),
                    rs.getObject("event_id") == null ? null : rs.getLong("event_id"),
                    rs.getString("event_name"),
                    rs.getString("event_category")));
        }, bookingId);
    }
}
