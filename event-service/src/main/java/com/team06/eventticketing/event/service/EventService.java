package com.team06.eventticketing.event.service;

import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventStatus;
import com.team06.eventticketing.event.repository.EventRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event updateEventDetails(Long id, Map<String, Object> updates) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        Map<String, Object> details = event.getDetails();
        if (details == null) {
            details = new LinkedHashMap<>();
        }
        if (updates != null && !updates.isEmpty()) {
            details.putAll(updates);
        }
        event.setDetails(details);

        return eventRepository.save(event);
    }

    public List<Event> findByDetailAttribute(String key, String value, EventStatus status) {
        if (status == null) {
            return eventRepository.findByDetailsAttribute(key, value);
        }
        return eventRepository.findByDetailsAttributeAndStatus(key, value, status.name());
    }
}
