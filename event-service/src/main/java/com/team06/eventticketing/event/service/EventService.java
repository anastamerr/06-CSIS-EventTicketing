package com.team06.eventticketing.event.service;

import com.team06.eventticketing.event.dto.EventRevenueDTO;
import com.team06.eventticketing.event.dto.VerifyEventSessionRequest;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventSession;
import com.team06.eventticketing.event.model.EventStatus;
import com.team06.eventticketing.event.repository.EventRepository;
import com.team06.eventticketing.event.repository.EventSessionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventSessionRepository eventSessionRepository;

    public EventService(EventRepository eventRepository, EventSessionRepository eventSessionRepository) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
    }

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public Event getEventById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    public Event updateEvent(Long id, Event event) {
        Event existingEvent = getEventById(id);
        existingEvent.setName(event.getName());
        existingEvent.setVenue(event.getVenue());
        existingEvent.setEventDate(event.getEventDate());
        existingEvent.setCategory(event.getCategory());
        existingEvent.setStatus(event.getStatus());
        existingEvent.setRating(event.getRating());
        existingEvent.setTotalRatings(event.getTotalRatings());
        existingEvent.setDetails(event.getDetails());
        return eventRepository.save(existingEvent);
    }

    public Event updateEventDetails(Long id, Map<String, Object> updates) {
        Event event = getEventById(id);

        Map<String, Object> details = event.getDetails() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(event.getDetails());
        if (updates != null && !updates.isEmpty()) {
            details.putAll(updates);
        }
        event.setDetails(details);

        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public EventRevenueDTO getEventRevenueSummary(Long eventId, LocalDate startDate, LocalDate endDate) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        Object[] result = eventRepository.findEventRevenueSummary(eventId, startDateTime, endDateTime);

        if (result == null) {
            return new EventRevenueDTO(event.getId(), event.getName(), 0L, 0.0, 0.0);
        }

        return new EventRevenueDTO(
                ((Number) result[0]).longValue(),
                (String) result[1],
                ((Number) result[2]).longValue(),
                result[3] == null ? 0.0 : ((Number) result[3]).doubleValue(),
                result[4] == null ? 0.0 : ((Number) result[4]).doubleValue()
        );
    }

    public List<Event> findByDetailAttribute(String key, String value, EventStatus status) {
        if (status == null) {
            return eventRepository.findByDetailsAttribute(key, value);
        }
        return eventRepository.findByDetailsAttributeAndStatus(key, value, status.name());
    }

    public void deleteEvent(Long id) {
        getEventById(id);
        eventRepository.deleteById(id);
    }

    @Transactional
    public Event verifyEventSession(Long eventId, Long sessionId, VerifyEventSessionRequest request) {
        if (request == null || request.getVerifiedBy() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verifiedBy is required");
        }

        eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        EventSession session = eventSessionRepository.findByIdWithEvent(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session does not belong to the specified event");
        }

        if (session.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot verify a session that already happened");
        }

        if (!eventRepository.existsAdminUserById(request.getVerifiedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifier must be an admin user");
        }

        session.setVerified(Boolean.TRUE);
        Map<String, Object> metadata = new LinkedHashMap<>(session.getMetadata());
        metadata.put("verifiedAt", LocalDateTime.now().toString());
        metadata.put("verifiedBy", request.getVerifiedBy());
        session.setMetadata(metadata);
        eventSessionRepository.save(session);

        return eventRepository.findByIdWithEventSessions(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }
}
