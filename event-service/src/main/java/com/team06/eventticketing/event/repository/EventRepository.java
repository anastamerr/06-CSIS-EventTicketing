package com.team06.eventticketing.event.repository;

import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventCategory;
import com.team06.eventticketing.event.model.EventStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByCategory(EventCategory category);

    List<Event> findByStatus(EventStatus status);

    List<Event> findByNameContainingIgnoreCase(String name);

    @Query(value = "select * from events e where e.details ->> :key = :value", nativeQuery = true)
    List<Event> findByDetailsAttribute(@Param("key") String key, @Param("value") String value);

    @Query(value = "select * from events e where e.details ->> :key = :value and e.status = :status", nativeQuery = true)
    List<Event> findByDetailsAttributeAndStatus(
            @Param("key") String key,
            @Param("value") String value,
            @Param("status") String status
    );

    @Query("SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.eventSessions WHERE e.id = :id")
    Optional<Event> findByIdWithEventSessions(@Param("id") Long id);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE id = :userId AND role = 'ADMIN')", nativeQuery = true)
    boolean existsAdminUserById(@Param("userId") Long userId);

    @Query(value = """
            SELECT
                e.id,
                e.name,
                COUNT(b.id),
                COALESCE(SUM(b.total_amount), 0),
                COALESCE(AVG(b.total_amount), 0)
            FROM events e
            LEFT JOIN bookings b
                ON b.event_id = e.id
               AND b.status = 'COMPLETED'
               AND b.booking_date >= :startDateTime
               AND b.booking_date < :endDateTime
            WHERE e.id = :eventId
            GROUP BY e.id, e.name
            """, nativeQuery = true)
    List<Object[]> findEventRevenueSummary(
            @Param("eventId") Long eventId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    @Query(value = "SELECT id, event_id, status FROM bookings WHERE id = :bookingId", nativeQuery = true)
    List<Object[]> findBookingById(@Param("bookingId") Long bookingId);

    @Query(value = """
        SELECT EXISTS (
            SELECT 1
            FROM bookings
            WHERE event_id = :eventId
              AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')
        )
        """, nativeQuery = true)
    boolean existsActiveBookingsForEvent(@Param("eventId") Long eventId);

    @Query("""
        SELECT DISTINCT e
        FROM Event e
        JOIN FETCH e.eventSessions s
        WHERE s.verified = false
        """)
    List<Event> findEventsWithUnverifiedSessions();

    @Query("""
        SELECT e
        FROM Event e
        WHERE (:category IS NULL OR e.category = :category)
          AND e.eventDate >= :startDateTime
          AND e.eventDate < :endDateTime
        ORDER BY e.eventDate ASC
        """)
    List<Event> searchEventsByCategoryAndDateRange(
            @Param("category") EventCategory category,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );






}
