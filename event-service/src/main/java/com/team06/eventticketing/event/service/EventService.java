package com.team06.eventticketing.event.service;

import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.event.adapter.EventRevenueAdapter;
import com.team06.eventticketing.event.adapter.TopEventAdapter;
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
import com.team06.eventticketing.event.search.EventFullTextSearchService;
import com.team06.eventticketing.event.search.EventSearchSyncService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventSessionRepository eventSessionRepository;
    private final EventRevenueAdapter eventRevenueAdapter;
    private final TopEventAdapter topEventAdapter;
    private final EventSearchSyncService eventSearchSyncService;
    private final EventFullTextSearchService eventFullTextSearchService;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public EventService(
            EventRepository eventRepository,
            EventSessionRepository eventSessionRepository,
            EventRevenueAdapter eventRevenueAdapter,
            TopEventAdapter topEventAdapter,
            EventSearchSyncService eventSearchSyncService,
            EventFullTextSearchService eventFullTextSearchService,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.eventRevenueAdapter = eventRevenueAdapter;
        this.topEventAdapter = topEventAdapter;
        this.eventSearchSyncService = eventSearchSyncService;
        this.eventFullTextSearchService = eventFullTextSearchService;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public EventService(EventRepository eventRepository, EventSessionRepository eventSessionRepository) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.eventRevenueAdapter = new EventRevenueAdapter();
        this.topEventAdapter = new TopEventAdapter();
        this.eventSearchSyncService = null;
        this.eventFullTextSearchService = null;
    }

    public EventService(
            EventRepository eventRepository,
            EventSessionRepository eventSessionRepository,
            EventFullTextSearchService eventFullTextSearchService
    ) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.eventRevenueAdapter = new EventRevenueAdapter();
        this.topEventAdapter = new TopEventAdapter();
        this.eventSearchSyncService = null;
        this.eventFullTextSearchService = eventFullTextSearchService;
    }

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public Event getEventById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    public Event createEvent(Event event) {
        Event saved = eventRepository.save(event);
        eventSearchSync(saved);
        notifyObservers("EVENT_CREATED", Map.of(
                "eventId", saved.getId(),
                "details", buildEventDetails(saved)));
        return saved;
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
        Event saved = eventRepository.save(existingEvent);
        eventSearchSync(saved);
        notifyObservers("EVENT_UPDATED", Map.of(
                "eventId", saved.getId(),
                "details", buildEventDetails(saved)));
        return saved;
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

        Event saved = eventRepository.save(event);
        eventSearchSync(saved);
        notifyObservers("DETAILS_UPDATED", Map.of(
                "eventId", saved.getId(),
                "details", buildEventDetails(saved)));
        return saved;
    }

    @Transactional(readOnly = true)
    public EventRevenueDTO getEventRevenueSummary(Long eventId, LocalDate startDate, LocalDate endDate) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
        List<Object[]> results = eventRepository.findEventRevenueSummary(eventId, startDateTime, endDateTime);
        if (results.isEmpty()) {
            return EventRevenueDTO.builder()
                    .eventId(event.getId())
                    .name(event.getName())
                    .totalBookings(0L)
                    .totalRevenue(0.0)
                    .averageBookingAmount(0.0)
                    .build();
        }
        return eventRevenueAdapter.adapt(results.get(0));
    }

    public List<Event> findByDetailAttribute(String key, String value, EventStatus status) {
        if (status == null) {
            return eventRepository.findByDetailsAttribute(key, value);
        }
        return eventRepository.findByDetailsAttributeAndStatus(key, value, status.name());
    }

    public void deleteEvent(Long id) {
        Event event = getEventById(id);
        notifyObservers("EVENT_DELETED", Map.of(
                "eventId", event.getId(),
                "details", buildEventDetails(event)));
        eventRepository.deleteById(id);
        eventSearchDelete(id);
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
        eventSearchSync(event);
        notifyObservers("RATED", Map.of(
                "eventId", event.getId(),
                "details", Map.of(
                        "rating", request.getRating(),
                        "bookingId", request.getBookingId())));
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

        LocalDateTime now = LocalDateTime.now();
        if (!session.getStartTime().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot verify a session that already happened");
        }

        if (!eventRepository.existsAdminUserById(request.getVerifiedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifier must be an admin user");
        }

        session.setVerified(Boolean.TRUE);
        Map<String, Object> metadata = new LinkedHashMap<>(session.getMetadata());
        metadata.put("verifiedAt", now.toString());
        metadata.put("verifiedBy", request.getVerifiedBy());
        session.setMetadata(metadata);
        eventSessionRepository.save(session);
        notifyObservers("SESSION_VERIFIED", Map.of(
                "eventId", eventId,
                "details", Map.of(
                        "sessionId", sessionId,
                        "verifiedBy", request.getVerifiedBy())));

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
        eventSearchSync(event);
        notifyObservers("STATUS_CHANGED", Map.of(
                "eventId", eventId,
                "details", Map.of("status", request.getStatus().name())));
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

                    return EventSessionAlertDTO.builder()
                            .eventId(event.getId())
                            .eventName(event.getName())
                            .eventStatus(event.getStatus())
                            .unverifiedSessions(unverifiedSessions)
                            .unverifiedCount(unverifiedSessions.size())
                            .build();
                })
                .toList();
    }


    @Transactional(readOnly = true)
    public List<TopEventDTO> getTopRatedEvents(int limit) {

        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be positive");
        }

        List<Object[]> results = eventRepository.findTopRatedEvents(limit);

        return results.stream().map(topEventAdapter::adapt).toList();
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

    public List<Event> searchEventsFullText(
            String query,
            EventCategory category,
            String venue,
            EventStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Double minRating,
            Double maxRating
    ) {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }

        if (minRating != null && maxRating != null && maxRating < minRating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRating must be greater than or equal to minRating");
        }

        if (eventFullTextSearchService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Full-text search is unavailable");
        }

        return eventFullTextSearchService.search(query, category, venue, status, startDate, endDate, minRating, maxRating);
    }

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String action, Object payload) {
        observers.forEach(observer -> observer.onEvent(action, payload));
    }

    private Map<String, Object> buildEventDetails(Event event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", event.getName());
        details.put("status", event.getStatus() == null ? null : event.getStatus().name());
        details.put("venue", event.getVenue());
        return details;
    }

    private void eventSearchSync(Event event) {
        if (eventSearchSyncService != null) {
            eventSearchSyncService.indexEvent(event);
        }
    }

    private void eventSearchDelete(Long eventId) {
        if (eventSearchSyncService != null) {
            eventSearchSyncService.removeEvent(eventId);
        }
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.EVENT_ACTIVITY, "event_events"));
        }
    }
}
