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

    @Query(value = "select * from events e where e.details ->> :key = :value and e.status::text = :status", nativeQuery = true)
    List<Event> findByDetailsAttributeAndStatus(
            @Param("key") String key,
            @Param("value") String value,
            @Param("status") String status
    );

    @Query("SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.eventSessions WHERE e.id = :id")
    Optional<Event> findByIdWithEventSessions(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT e
        FROM Event e
        JOIN FETCH e.eventSessions s
        WHERE s.verified = false
        """)
    List<Event> findEventsWithUnverifiedSessions();

    @Query(value = """
    SELECT e.id, e.name, e.rating, 0 as totalBookings
    FROM events e
    ORDER BY e.rating DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findTopRatedEvents(@Param("limit") int limit);
    List<Event> findByEventDateGreaterThanEqualAndEventDateLessThanOrderByEventDateAsc(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

    List<Event> findByCategoryAndEventDateGreaterThanEqualAndEventDateLessThanOrderByEventDateAsc(
            EventCategory category,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );






}
