package com.team06.eventticketing.event.controller;

import com.team06.eventticketing.common.cache.CachedDetail;
import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.event.dto.EventDashboardDTO;
import com.team06.eventticketing.event.dto.EventRevenueDTO;
import com.team06.eventticketing.event.dto.EventSessionAlertDTO;
import com.team06.eventticketing.event.dto.AvgCapacityDTO;
import com.team06.eventticketing.event.dto.RateEventRequest;
import com.team06.eventticketing.event.dto.TopEventDTO;
import com.team06.eventticketing.event.dto.UpdateEventStatusRequest;
import com.team06.eventticketing.event.dto.VenueCoordsDTO;
import com.team06.eventticketing.event.dto.VerifyEventSessionRequest;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventCategory;
import com.team06.eventticketing.event.model.EventStatus;
import com.team06.eventticketing.event.service.EventService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Event createEvent(@RequestBody Event event) {
        return eventService.createEvent(event);
    }

    @GetMapping
    public List<Event> getAllEvents() {
        return eventService.getAllEvents();
    }

    @GetMapping("/{id}")
    @CachedDetail(service = "event-service", entity = "event", key = "#id", ttlSeconds = 900)
    public Event getEventById(@PathVariable Long id) {
        return eventService.getEventById(id);
    }

    @GetMapping("/{id}/revenue")
    @CachedFeature(service = "event-service", featureId = "S2-F3", ttlSeconds = 600)
    public EventRevenueDTO getEventRevenueSummary(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return eventService.getEventRevenueSummary(id, startDate, endDate);
    }

    @GetMapping("/{id}/sessions/avg-capacity")
    @CachedFeature(service = "event-service", featureId = "S2-AVG-CAPACITY", ttlSeconds = 600)
    public AvgCapacityDTO getEventAverageSessionCapacity(@PathVariable Long id) {
        return eventService.getEventAverageSessionCapacity(id);
    }

    @GetMapping("/{id}/venue-coords")
    @CachedFeature(service = "event-service", featureId = "S2-VENUE-COORDS", ttlSeconds = 600)
    public VenueCoordsDTO getEventVenueCoords(@PathVariable Long id) {
        return eventService.getEventVenueCoords(id);
    }

    @GetMapping("/{id}/dashboard")
    public EventDashboardDTO getEventDashboard(@PathVariable Long id) {
        return eventService.getEventDashboard(id);
    }

    @PostMapping("/{id}/index")
    @ResponseStatus(HttpStatus.OK)
    public void indexEventForSearch(@PathVariable Long id) {
        eventService.indexEventForSearch(id);
    }

    @PostMapping("/{id}/rate")
    @ResponseStatus(HttpStatus.OK)
    @InvalidateServiceCaches(
            service = "event-service",
            featurePrefix = "S2-",
            detailKeys = {"'event-service::event::' + #id"})
    public void rateEvent(@PathVariable Long id, @RequestBody RateEventRequest request) {
        eventService.rateEvent(id, request);
    }

    @PutMapping("/{id}")
    @InvalidateServiceCaches(
            service = "event-service",
            featurePrefix = "S2-",
            detailKeys = {"'event-service::event::' + #id"})
    public Event updateEvent(@PathVariable Long id, @RequestBody Event event) {
        return eventService.updateEvent(id, event);
    }

    @PutMapping("/{id}/details")
    @InvalidateServiceCaches(
            service = "event-service",
            featurePrefix = "S2-",
            detailKeys = {"'event-service::event::' + #id"})
    public Event updateEventDetails(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return eventService.updateEventDetails(id, updates);
    }

    @GetMapping("/details/search")
    @CachedFeature(service = "event-service", featureId = "S2-F5", ttlSeconds = 300)
    public List<Event> searchByDetails(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) EventStatus status
    ) {
        return eventService.findByDetailAttribute(key, value, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @InvalidateServiceCaches(
            service = "event-service",
            featurePrefix = "S2-",
            detailKeys = {"'event-service::event::' + #id"})
    public void deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
    }

    @PutMapping("/{eventId}/sessions/{sessionId}/verify")
    @InvalidateServiceCaches(
            service = "event-service",
            featurePrefix = "S2-",
            detailKeys = {"'event-service::event::' + #eventId", "'event-service::event-session::' + #sessionId"})
    public Event verifyEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody VerifyEventSessionRequest request
    ) {
        return eventService.verifyEventSession(eventId, sessionId, request);
    }

    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.OK)
    @InvalidateServiceCaches(
            service = "event-service",
            featurePrefix = "S2-",
            detailKeys = {"'event-service::event::' + #id"})
    public void updateEventStatus(@PathVariable Long id, @RequestBody UpdateEventStatusRequest request) {
        eventService.updateEventStatus(id, request);
    }
    @GetMapping("/sessions/unverified")
    @CachedFeature(service = "event-service", featureId = "S2-F6", ttlSeconds = 600)
    public List<EventSessionAlertDTO> getEventsWithUnverifiedSessions() {
        return eventService.getEventsWithUnverifiedSessions();
    }

    @GetMapping("/reports/top-rated")
    @CachedFeature(service = "event-service", featureId = "S2-F9", ttlSeconds = 600)
    public List<TopEventDTO> getTopRatedEvents(
            @RequestParam int limit
    ) {
        return eventService.getTopRatedEvents(limit);
    }

    @GetMapping("/search")
    @CachedFeature(service = "event-service", featureId = "S2-F1", ttlSeconds = 300)
    public List<Event> searchEvents(
            @RequestParam(required = false) EventCategory category,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return eventService.searchEvents(category, startDate, endDate);
    }

    @GetMapping("/search/full-text")
    @CachedFeature(service = "event-service", featureId = "S2-F10", ttlSeconds = 300)
    public List<Event> searchEventsFullText(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) EventCategory category,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating
    ) {
        return eventService.searchEventsFullText(query, category, venue, status, startDate, endDate, minRating, maxRating);
    }

}
