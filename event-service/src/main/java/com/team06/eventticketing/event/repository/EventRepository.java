package com.team06.eventticketing.event.repository;

import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventCategory;
import com.team06.eventticketing.event.model.EventStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByCategory(EventCategory category);

    List<Event> findByStatus(EventStatus status);

    List<Event> findByNameContainingIgnoreCase(String name);

    @Query(value = "select * from events e where e.details ->> :key = :value", nativeQuery = true)
    List<Event> findByDetailsAttribute(@Param("key") String key, @Param("value") String value);

    @Query(value = "select * from events e where e.details ->> :key = :value and e.status = :status",
        nativeQuery = true)
    List<Event> findByDetailsAttributeAndStatus(@Param("key") String key, @Param("value") String value,
        @Param("status") String status);
}
