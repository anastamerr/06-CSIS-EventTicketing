package com.team06.eventticketing.event.service;

import com.team06.eventticketing.event.dto.EventRevenueDTO;
import com.team06.eventticketing.event.dto.EventSessionAlertDTO;
import com.team06.eventticketing.event.dto.RateEventRequest;
import com.team06.eventticketing.event.dto.TopEventDTO;
import com.team06.eventticketing.event.dto.UpdateEventStatusRequest;
import com.team06.eventticketing.event.dto.VerifyEventSessionRequest;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventCategory;
import com.team06.eventticketing.event.model.EventSession;
import com.team06.eventticketing.event.model.EventStatus;
import com.team06.eventticketing.event.repository.EventRepository;
import com.team06.eventticketing.event.repository.EventSessionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
        List<Object[]> results = eventRepository.findEventRevenueSummary(eventId, startDateTime, endDateTime);
        if (results.isEmpty()) {
            return new EventRevenueDTO(event.getId(), event.getName(), 0L, 0.0, 0.0);
        }
        Object[] result = results.get(0);

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
    public void rateEvent(Long eventId, RateEventRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        if (request == null || request.getBookingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookingId is required");
        }

        List<Object[]> bookings = eventRepository.findBookingById(request.getBookingId());
        if (bookings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        Object[] booking = bookings.get(0);

        Long bookingEventId = booking[1] == null ? null : ((Number) booking[1]).longValue();
        String bookingStatus = booking[2] == null ? null : booking[2].toString();

        if (!eventId.equals(bookingEventId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking does not belong to the specified event");
        }

        if (!"COMPLETED".equals(bookingStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking must be completed");
        }

        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }

        double oldRating = event.getRating() == null ? 0.0 : event.getRating();
        int totalRatings = event.getTotalRatings() == null ? 0 : event.getTotalRatings();
        double newRating = ((oldRating * totalRatings) + request.getRating()) / (totalRatings + 1);

        event.setRating(newRating);
        event.setTotalRatings(totalRatings + 1);
        eventRepository.save(event);
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
    @Transactional
    public void updateEventStatus(Long eventId, UpdateEventStatusRequest request) {
        if (request == null || request.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        if (request.getStatus() == EventStatus.CANCELLED
                && eventRepository.existsActiveBookingsForEvent(eventId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot cancel event with active bookings"
            );
        }
        event.setStatus(request.getStatus());
        eventRepository.save(event);
    }
    @Transactional(readOnly = true)
    public List<EventSessionAlertDTO> getEventsWithUnverifiedSessions() {
        return eventRepository.findEventsWithUnverifiedSessions()
                .stream()
                .map(event -> {
                    List<EventSession> unverifiedSessions = event.getEventSessions()
                            .stream()
                            .filter(session -> Boolean.FALSE.equals(session.getVerified()))
                            .collect(Collectors.toList());

                    return new EventSessionAlertDTO(
                            event.getId(),
                            event.getName(),
                            event.getStatus(),
                            unverifiedSessions,
                            unverifiedSessions.size()
                    );
                })
                .toList();
    }


    @Transactional(readOnly = true)
    public List<TopEventDTO> getTopRatedEvents(int limit) {

        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be positive");
        }

        List<Object[]> results = eventRepository.findTopRatedEvents(limit);

        return results.stream().map(row -> new TopEventDTO(
                ((Number) row[0]).longValue(),
                (String) row[1],
                row[2] == null ? 0.0 : ((Number) row[2]).doubleValue(),
                ((Number) row[3]).longValue()
        )).toList();
    }

    public List<Event> searchEvents(EventCategory category, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }

        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        if (category == null) {
            return eventRepository.findByEventDateGreaterThanEqualAndEventDateLessThanOrderByEventDateAsc(
                    startDateTime,
                    endDateTime
            );
        }

        return eventRepository.findByCategoryAndEventDateGreaterThanEqualAndEventDateLessThanOrderByEventDateAsc(
                category,
                startDateTime,
                endDateTime
        );
    }
}
