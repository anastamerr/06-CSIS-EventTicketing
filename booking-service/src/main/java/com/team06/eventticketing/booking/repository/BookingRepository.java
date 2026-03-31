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

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.eventId = :eventId AND b.status IN " +
            "(com.team06.eventticketing.booking.model.BookingStatus.PENDING, " +
            "com.team06.eventticketing.booking.model.BookingStatus.CONFIRMED, " +
            "com.team06.eventticketing.booking.model.BookingStatus.CHECKED_IN)")
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
        SELECT
            COUNT(*) AS totalBookings,
            COALESCE(COUNT(*) FILTER (WHERE status = 'COMPLETED'), 0) AS completedBookings,
            COALESCE(COUNT(*) FILTER (WHERE status = 'CANCELLED'), 0) AS cancelledBookings,
            COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_amount ELSE 0 END), 0) AS totalRevenue,
            COALESCE(AVG(CASE WHEN status = 'COMPLETED' THEN total_amount END), 0) AS averageBookingAmount
        FROM bookings
        WHERE booking_date BETWEEN :startDate AND :endDate
        """, nativeQuery = true)
    List<Object[]> findAnalyticsByDateRange(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);
}
