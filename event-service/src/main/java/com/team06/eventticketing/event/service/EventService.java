package com.team06.eventticketing.event.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.contracts.dto.EventBookingRevenueDTO;
import com.team06.eventticketing.contracts.dto.EventTicketSummaryDTO;
import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.events.EventRatedEvent;
import com.team06.eventticketing.contracts.events.EventStatusChangedEvent;
import com.team06.eventticketing.contracts.feign.BookingServiceClient;
import com.team06.eventticketing.contracts.feign.TicketServiceClient;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import com.team06.eventticketing.event.adapter.TopEventAdapter;
import com.team06.eventticketing.event.dto.AvgCapacityDTO;
import com.team06.eventticketing.event.dto.EventDashboardDTO;
import com.team06.eventticketing.event.dto.EventRevenueDTO;
import com.team06.eventticketing.event.dto.EventSessionAlertDTO;
import com.team06.eventticketing.event.dto.RateEventRequest;
import com.team06.eventticketing.event.dto.TopEventDTO;
import com.team06.eventticketing.event.dto.UpdateEventStatusRequest;
import com.team06.eventticketing.event.dto.VenueCoordsDTO;
import com.team06.eventticketing.event.dto.VerifyEventSessionRequest;
import com.team06.eventticketing.event.messaging.EventEventPublisher;
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
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private static final List<String> INDEXED_FIELDS = List.of(
            "id",
            "name",
            "category",
            "venue",
            "description",
            "eventDate",
            "rating",
            "status"
    );

    private final EventRepository eventRepository;
    private final EventSessionRepository eventSessionRepository;
    private final TopEventAdapter topEventAdapter;
    private final EventSearchSyncService eventSearchSyncService;
    private final EventFullTextSearchService eventFullTextSearchService;
    private final RedisCacheService redisCacheService;
    private final BookingServiceClient bookingServiceClient;
    private final UserServiceClient userServiceClient;
    private final TicketServiceClient ticketServiceClient;
    private final EventEventPublisher eventEventPublisher;
    private final JavaType eventDashboardType;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public EventService(
            EventRepository eventRepository,
            EventSessionRepository eventSessionRepository,
            TopEventAdapter topEventAdapter,
            EventSearchSyncService eventSearchSyncService,
            EventFullTextSearchService eventFullTextSearchService,
            RedisCacheService redisCacheService,
            BookingServiceClient bookingServiceClient,
            UserServiceClient userServiceClient,
            TicketServiceClient ticketServiceClient,
            EventEventPublisher eventEventPublisher,
            ObjectMapper objectMapper,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.topEventAdapter = topEventAdapter;
        this.eventSearchSyncService = eventSearchSyncService;
        this.eventFullTextSearchService = eventFullTextSearchService;
        this.redisCacheService = redisCacheService;
        this.bookingServiceClient = bookingServiceClient;
        this.userServiceClient = userServiceClient;
        this.ticketServiceClient = ticketServiceClient;
        this.eventEventPublisher = eventEventPublisher;
        this.eventDashboardType = objectMapper.getTypeFactory().constructType(EventDashboardDTO.class);
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public EventService(
            EventRepository eventRepository,
            EventSessionRepository eventSessionRepository,
            EventFullTextSearchService eventFullTextSearchService,
            BookingServiceClient bookingServiceClient,
            UserServiceClient userServiceClient,
            TicketServiceClient ticketServiceClient,
            EventEventPublisher eventEventPublisher
    ) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.topEventAdapter = new TopEventAdapter();
        this.eventSearchSyncService = null;
        this.eventFullTextSearchService = eventFullTextSearchService;
        this.redisCacheService = null;
        this.bookingServiceClient = bookingServiceClient;
        this.userServiceClient = userServiceClient;
        this.ticketServiceClient = ticketServiceClient;
        this.eventEventPublisher = eventEventPublisher;
        this.eventDashboardType = null;
    }

    public EventService(
            EventRepository eventRepository,
            EventSessionRepository eventSessionRepository,
            EventSearchSyncService eventSearchSyncService,
            EventFullTextSearchService eventFullTextSearchService
    ) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.topEventAdapter = new TopEventAdapter();
        this.eventSearchSyncService = eventSearchSyncService;
        this.eventFullTextSearchService = eventFullTextSearchService;
        this.redisCacheService = null;
        this.bookingServiceClient = null;
        this.userServiceClient = null;
        this.ticketServiceClient = null;
        this.eventEventPublisher = null;
        this.eventDashboardType = null;
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
        eventSearchSync(saved, "auto_crud_create");
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
        eventSearchSync(saved, "auto_crud_update");
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
        eventSearchSync(saved, "auto_crud_update");
        notifyObservers("DETAILS_UPDATED", Map.of(
                "eventId", saved.getId(),
                "details", buildEventDetails(saved)));
        return saved;
    }

    public void indexEventForSearch(Long id) {
        Event event = getEventById(id);
        eventSearchSync(event, "explicit");
    }

    @Transactional(readOnly = true)
    public EventRevenueDTO getEventRevenueSummary(Long eventId, LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        withEventMdc(eventId);
        try {
            EventBookingRevenueDTO revenue = getEventRevenueOrZero(eventId, startDate.toString(), endDate.toString());
            log.info("Feign booking-service revenue lookup succeeded");
            return EventRevenueDTO.builder()
                    .eventId(event.getId())
                    .name(event.getName())
                    .totalBookings(revenue.totalBookings())
                    .totalRevenue(revenue.totalRevenue())
                    .averageBookingAmount(revenue.averageBookingAmount())
                    .build();
        } finally {
            clearEventMdc();
        }
    }

    @Transactional(readOnly = true)
    public EventDashboardDTO getEventDashboard(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        notifyObservers("DASHBOARD_VIEWED", Map.of(
                "eventId", event.getId(),
                "details", Map.of("eventId", event.getId())));

        EventDashboardDTO cached = getCachedEventDashboard(eventId);
        if (cached != null) {
            return cached;
        }

        withEventMdc(eventId);
        try {
            EventBookingRevenueDTO revenue = getEventRevenueOrZero(eventId, "1970-01-01", "2999-12-31");
            EventTicketSummaryDTO ticketSummary = getEventTicketSummaryOrZero(eventId);
            long totalTicketsSold = ticketSummary.totalTicketsSold();
            double averageAttendanceRate = totalTicketsSold == 0
                    ? 0.0
                    : (double) ticketSummary.usedCount() / totalTicketsSold;
            log.info("Feign dashboard aggregation lookups succeeded");
            EventDashboardDTO dashboard = EventDashboardDTO.builder()
                    .eventId(event.getId())
                    .name(event.getName())
                    .totalBookings(revenue.totalBookings())
                    .totalTicketsSold(totalTicketsSold)
                    .totalRevenue(revenue.totalRevenue())
                    .averageAttendanceRate(averageAttendanceRate)
                    .averageRating(event.getRating() == null ? 0.0 : event.getRating())
                    .build();
            putCachedEventDashboard(eventId, dashboard);
            return dashboard;
        } finally {
            clearEventMdc();
        }
    }

    @Transactional(readOnly = true)
    public AvgCapacityDTO getEventAverageSessionCapacity(Long eventId) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        withEventMdc(eventId);
        try {
            return new AvgCapacityDTO(eventSessionRepository.findAverageCapacityByEventId(eventId));
        } finally {
            clearEventMdc();
        }
    }

    @Transactional(readOnly = true)
    public VenueCoordsDTO getEventVenueCoords(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        withEventMdc(eventId);
        try {
            Map<String, Object> details = event.getDetails();
            return new VenueCoordsDTO(
                    asDouble(details == null ? null : details.get("venueLat")),
                    asDouble(details == null ? null : details.get("venueLon")));
        } finally {
            clearEventMdc();
        }
    }

    public List<Event> findByDetailAttribute(String key, String value, EventStatus status) {
        if (status == null) {
            return eventRepository.findByDetailsAttribute(key, value);
        }
        return eventRepository.findByDetailsAttributeAndStatus(key, value, status.name());
    }

    public void deleteEvent(Long id) {
        Event event = getEventById(id);
        eventSearchDelete(id);
        notifyObservers("EVENT_DELETED", Map.of(
                "eventId", event.getId(),
                "details", buildEventDetails(event)));
        eventRepository.deleteById(id);
    }

    @Transactional
    public void rateEvent(Long eventId, RateEventRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        if (request == null || request.getBookingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookingId is required");
        }

        withEventMdc(eventId);
        try {
            BookingDTO booking;
            booking = bookingServiceClient.getBooking(request.getBookingId());
            log.info("Feign booking-service booking lookup succeeded");
            if (booking == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
            }

            if (!eventId.equals(booking.eventId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking does not belong to the specified event");
            }

            if (!"COMPLETED".equals(booking.status())) {
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
            eventSearchSync(event, "auto_crud_update");
            publishRated(event, request, booking);
            notifyObservers("RATED", Map.of(
                    "eventId", event.getId(),
                    "details", Map.of(
                            "rating", request.getRating(),
                            "bookingId", request.getBookingId())));
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Booking service temporarily unavailable", exception);
        } finally {
            clearEventMdc();
        }
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

        withEventMdc(eventId);
        try {
            UserDTO verifier;
            verifier = userServiceClient.getUser(request.getVerifiedBy());
            log.info("Feign user-service verifier lookup succeeded");

            if (verifier == null || !"ADMIN".equals(verifier.role())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifier must be an admin user");
            }

            session.setVerified(Boolean.TRUE);
            Map<String, Object> metadata = session.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(session.getMetadata());
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
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifier must be an admin user");
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable", exception);
        } finally {
            clearEventMdc();
        }
    }
    @Transactional
    public void updateEventStatus(Long eventId, UpdateEventStatusRequest request) {
        if (request == null || request.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        EventStatus oldStatus = event.getStatus();

        withEventMdc(eventId);
        try {
            if (request.getStatus() == EventStatus.CANCELLED
                    && bookingServiceClient.getEventActiveBookingCount(eventId) > 0) {
                log.info("Feign booking-service active booking count blocked cancellation");
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot cancel event with active bookings"
                );
            }
            event.setStatus(request.getStatus());
            eventRepository.save(event);
            eventSearchSync(event, "auto_crud_update");
            publishStatusChanged(event, oldStatus);
            notifyObservers("STATUS_CHANGED", Map.of(
                    "eventId", eventId,
                    "details", Map.of("status", request.getStatus().name())));
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event booking summary not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Booking service temporarily unavailable", exception);
        } finally {
            clearEventMdc();
        }
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

    private void eventSearchSync(Event event, String source) {
        if (eventSearchSyncService != null) {
            eventSearchSyncService.indexEvent(event);
            notifyObservers("INDEXED", Map.of(
                    "eventId", event.getId(),
                    "details", buildIndexedDetails(event.getId(), source)));
        }
    }

    private void eventSearchDelete(Long eventId) {
        if (eventSearchSyncService != null) {
            eventSearchSyncService.removeEvent(eventId);
        }
    }

    private EventDashboardDTO getCachedEventDashboard(Long eventId) {
        if (redisCacheService == null || eventDashboardType == null) {
            return null;
        }
        return redisCacheService.get(eventDashboardCacheKey(eventId), eventDashboardType);
    }

    private void putCachedEventDashboard(Long eventId, EventDashboardDTO dashboard) {
        if (redisCacheService != null) {
            redisCacheService.put(eventDashboardCacheKey(eventId), dashboard, 600);
        }
    }

    private String eventDashboardCacheKey(Long eventId) {
        return "event-service::S2-F12::" + eventId;
    }

    private void publishStatusChanged(Event event, EventStatus oldStatus) {
        if (eventEventPublisher != null) {
            eventEventPublisher.publishStatusChanged(new EventStatusChangedEvent(
                    event.getId(),
                    oldStatus == null ? null : oldStatus.name(),
                    event.getStatus() == null ? null : event.getStatus().name()));
        }
    }

    private EventBookingRevenueDTO getEventRevenueOrZero(Long eventId, String startDate, String endDate) {
        try {
            return bookingServiceClient.getEventRevenue(eventId, startDate, endDate);
        } catch (FeignException.NotFound exception) {
            return new EventBookingRevenueDTO(0L, 0.0, 0.0);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Booking service temporarily unavailable", exception);
        }
    }

    private EventTicketSummaryDTO getEventTicketSummaryOrZero(Long eventId) {
        try {
            return ticketServiceClient.getEventTicketSummary(eventId);
        } catch (FeignException.NotFound exception) {
            return new EventTicketSummaryDTO(0L, 0L);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ticket service temporarily unavailable", exception);
        }
    }

    private void publishRated(Event event, RateEventRequest request, BookingDTO booking) {
        if (eventEventPublisher != null) {
            eventEventPublisher.publishRated(new EventRatedEvent(
                    event.getId(),
                    request.getBookingId(),
                    (double) request.getRating(),
                    booking.userId()));
        }
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.valueOf(value.toString());
    }

    private void withEventMdc(Long eventId) {
        if (eventId != null) {
            MDC.put("eventId", eventId.toString());
        }
    }

    private void clearEventMdc() {
        MDC.remove("eventId");
    }

    private Map<String, Object> buildIndexedDetails(Long eventId, String source) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", eventId);
        details.put("indexedFields", INDEXED_FIELDS);
        details.put("source", source);
        return details;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.EVENT_ACTIVITY, "event_events"));
        }
    }
}
