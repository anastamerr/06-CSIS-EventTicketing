package com.team06.eventticketing.booking.repository;

import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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
              AND status::text IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETING', 'PAYMENT_PENDING')
            """, nativeQuery = true)
    long countActiveBookingsByEventId(@Param("eventId") Long eventId);

    @Query(value = """
            SELECT COUNT(*)
            FROM bookings
            WHERE user_id = :userId
              AND status::text IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETING', 'PAYMENT_PENDING')
            """, nativeQuery = true)
    long countActiveBookingsByUserId(@Param("userId") Long userId);

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

    @Query(
            value = "SELECT * FROM bookings b WHERE b.metadata ->> :key = :value ORDER BY b.booking_date DESC",
            nativeQuery = true
    )
    List<Booking> findByMetadataField(@Param("key") String key, @Param("value") String value);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, BookingStatus status);

    @Query(value = """
            SELECT COALESCE(SUM(total_amount), 0)
            FROM bookings
            WHERE user_id = :userId
              AND status::text = 'COMPLETED'
            """, nativeQuery = true)
    BigDecimal sumCompletedAmountByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(SUM(total_amount), 0)
            FROM bookings
            WHERE user_id = :userId
              AND status::text = 'COMPLETED'
              AND booking_date BETWEEN :startDate AND :endDate
            """, nativeQuery = true)
    BigDecimal sumCompletedAmountByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    long countByEventId(Long eventId);

    long countByEventIdAndBookingDateBetween(Long eventId, LocalDateTime startDate, LocalDateTime endDate);

    long countByEventIdAndStatus(Long eventId, BookingStatus status);

    long countByEventIdAndStatusAndBookingDateBetween(
            Long eventId,
            BookingStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    @Query(value = """
            SELECT COALESCE(SUM(total_amount), 0)
            FROM bookings
            WHERE event_id = :eventId
              AND status::text = 'COMPLETED'
              AND booking_date BETWEEN :startDate AND :endDate
            """, nativeQuery = true)
    BigDecimal sumCompletedAmountByEventIdAndDateRange(
            @Param("eventId") Long eventId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query(value = """
        SELECT
            COUNT(*) AS totalBookings,
            COALESCE(COUNT(*) FILTER (WHERE b.status = 'COMPLETED'), 0) AS completedBookings,
            COALESCE(COUNT(*) FILTER (WHERE b.status = 'CANCELLED'), 0) AS cancelledBookings,
            COALESCE(SUM(CASE WHEN b.status = 'COMPLETED' THEN COALESCE(b.total_amount, 0) ELSE 0 END), 0) AS totalRevenue,
            COALESCE(AVG(CASE WHEN b.status = 'COMPLETED' THEN COALESCE(b.total_amount, 0) END), 0) AS averageBookingAmount
        FROM bookings b
        WHERE b.booking_date BETWEEN :startDate AND :endDate
        """, nativeQuery = true)
    List<Object[]> findAnalyticsByDateRange(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        WITH filtered_bookings AS (
            SELECT b.id, b.status, b.total_amount
            FROM bookings b
            WHERE b.booking_date BETWEEN :startDate AND :endDate
        ),
        totals AS (
            SELECT
                COUNT(f.id) AS total_bookings,
                COALESCE(SUM(CASE WHEN f.status = 'COMPLETED' THEN COALESCE(f.total_amount, 0) ELSE 0 END), 0) AS total_revenue,
                COALESCE(COUNT(*) FILTER (WHERE f.status = 'COMPLETED'), 0) AS completed_count,
                COALESCE(COUNT(*) FILTER (
                    WHERE f.status IN ('CONFIRMED', 'CHECKED_IN', 'COMPLETED')
                ), 0) AS converted_count
            FROM filtered_bookings f
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
