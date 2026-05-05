package com.team06.eventticketing.booking.repository;

import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByEventId(Long eventId);

    List<Booking> findByStatus(BookingStatus status);

    @Query(value = """
            SELECT COUNT(*)
            FROM bookings
            WHERE event_id = :eventId
              AND status::text IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')
            """, nativeQuery = true)
    long countActiveBookingsByEventId(@Param("eventId") Long eventId);

    @Query(value = "SELECT AVG(capacity) FROM event_sessions WHERE event_id = :eventId", nativeQuery = true)
    Double findAverageSessionCapacityByEventId(@Param("eventId") Long eventId);

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.bookingItems WHERE b.id = :id")
    Optional<Booking> findByIdWithBookingItems(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.bookingItems WHERE b.id = :id")
    Optional<Booking> findByIdWithBookingItemsForUpdate(@Param("id") Long id);

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.bookingItems")
    List<Booking> findAllWithBookingItems();

    List<Booking> findByBookingDateBetweenOrderByBookingDateDesc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Booking> findByStatusAndBookingDateBetweenOrderByBookingDateDesc(
            BookingStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    @Query(value = "SELECT id, status FROM events WHERE id = :eventId", nativeQuery = true)
    List<Object[]> findEventById(@Param("eventId") Long eventId);
    @Query(
            value = "SELECT * FROM bookings b WHERE b.metadata ->> :key = :value ORDER BY b.booking_date DESC",
            nativeQuery = true
    )
    List<Booking> findByMetadataField(@Param("key") String key, @Param("value") String value);

    @Query(value = """
        WITH sales_by_booking AS (
            SELECT booking_id, SUM(amount) AS sale_amount
            FROM ticket_sales
            GROUP BY booking_id
        )
        SELECT
            COUNT(*) AS totalBookings,
            COALESCE(COUNT(*) FILTER (WHERE b.status = 'COMPLETED'), 0) AS completedBookings,
            COALESCE(COUNT(*) FILTER (WHERE b.status = 'CANCELLED'), 0) AS cancelledBookings,
            COALESCE(SUM(CASE WHEN b.status = 'COMPLETED' THEN COALESCE(s.sale_amount, 0) ELSE 0 END), 0) AS totalRevenue,
            COALESCE(AVG(CASE WHEN b.status = 'COMPLETED' THEN COALESCE(s.sale_amount, 0) END), 0) AS averageBookingAmount
        FROM bookings b
        LEFT JOIN sales_by_booking s ON s.booking_id = b.id
        WHERE b.booking_date BETWEEN :startDate AND :endDate
        """, nativeQuery = true)
    List<Object[]> findAnalyticsByDateRange(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        WITH filtered_bookings AS (
            SELECT b.id, b.status
            FROM bookings b
            WHERE b.booking_date BETWEEN :startDate AND :endDate
        ),
        sales_by_booking AS (
            SELECT booking_id, SUM(amount) AS sale_amount
            FROM ticket_sales
            GROUP BY booking_id
        ),
        totals AS (
            SELECT
                COUNT(f.id) AS total_bookings,
                COALESCE(SUM(CASE WHEN f.status = 'COMPLETED' THEN COALESCE(s.sale_amount, 0) ELSE 0 END), 0) AS total_revenue,
                COALESCE(COUNT(*) FILTER (WHERE f.status = 'COMPLETED'), 0) AS completed_count,
                COALESCE(COUNT(*) FILTER (
                    WHERE f.status IN ('CONFIRMED', 'CHECKED_IN', 'COMPLETED')
                ), 0) AS converted_count
            FROM filtered_bookings f
            LEFT JOIN sales_by_booking s ON s.booking_id = f.id
        ),
        status_counts AS (
            SELECT status::text AS booking_status, COUNT(*) AS status_count
            FROM filtered_bookings
            GROUP BY status
        )
        SELECT
            totals.total_bookings,
            totals.total_revenue,
            totals.completed_count,
            totals.converted_count,
            status_counts.booking_status,
            COALESCE(status_counts.status_count, 0)
        FROM totals
        LEFT JOIN status_counts ON TRUE
        """, nativeQuery = true)
    List<Object[]> findDashboardAnalytics(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
}
