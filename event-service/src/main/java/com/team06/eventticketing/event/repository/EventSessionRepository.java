package com.team06.eventticketing.event.repository;

import com.team06.eventticketing.event.model.EventSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSessionRepository extends JpaRepository<EventSession, Long> {

    List<EventSession> findByEventId(Long eventId);

    List<EventSession> findByEventIdOrderByStartTimeAsc(Long eventId);
}
