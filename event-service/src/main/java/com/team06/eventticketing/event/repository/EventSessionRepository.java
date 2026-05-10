package com.team06.eventticketing.event.repository;

import com.team06.eventticketing.event.model.EventSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventSessionRepository extends JpaRepository<EventSession, Long> {

    List<EventSession> findByEventId(Long eventId);

    List<EventSession> findByEventIdOrderByStartTimeAsc(Long eventId);

    @Query("SELECT s FROM EventSession s JOIN FETCH s.event WHERE s.id = :id")
    Optional<EventSession> findByIdWithEvent(@Param("id") Long id);

    @Query("SELECT COALESCE(AVG(s.capacity), 0.0) FROM EventSession s WHERE s.event.id = :eventId")
    Double findAverageCapacityByEventId(@Param("eventId") Long eventId);
}
