package com.team06.eventticketing.event.controller;

import com.team06.eventticketing.event.dto.EventRevenueDTO;
import com.team06.eventticketing.event.dto.EventSessionAlertDTO;
import com.team06.eventticketing.event.dto.RateEventRequest;
import com.team06.eventticketing.event.dto.TopEventDTO;
import com.team06.eventticketing.event.dto.UpdateEventStatusRequest;
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
    public Event getEventById(@PathVariable Long id) {
        return eventService.getEventById(id);
    }

    @GetMapping("/{id}/revenue")
    public EventRevenueDTO getEventRevenueSummary(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return eventService.getEventRevenueSummary(id, startDate, endDate);
    }

    @PostMapping("/{id}/rate")
    @ResponseStatus(HttpStatus.OK)
    public void rateEvent(@PathVariable Long id, @RequestBody RateEventRequest request) {
        eventService.rateEvent(id, request);
    }

    @PutMapping("/{id}")
    public Event updateEvent(@PathVariable Long id, @RequestBody Event event) {
        return eventService.updateEvent(id, event);
    }

    @PutMapping("/{id}/details")
    public Event updateEventDetails(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return eventService.updateEventDetails(id, updates);
    }

    @GetMapping("/details/search")
    public List<Event> searchByDetails(
            @RequestParam String key,
            @RequestParam String value,
            @RequestParam(required = false) EventStatus status
    ) {
        return eventService.findByDetailAttribute(key, value, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
    }

    @PutMapping("/{eventId}/sessions/{sessionId}/verify")
    public Event verifyEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody VerifyEventSessionRequest request
    ) {
        return eventService.verifyEventSession(eventId, sessionId, request);
    }

    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.OK)
    public void updateEventStatus(@PathVariable Long id, @RequestBody UpdateEventStatusRequest request) {
        eventService.updateEventStatus(id, request);
    }
    @GetMapping("/sessions/unverified")
    public List<EventSessionAlertDTO> getEventsWithUnverifiedSessions() {
        return eventService.getEventsWithUnverifiedSessions();
    }

    @GetMapping("/reports/top-rated")
    public List<TopEventDTO> getTopRatedEvents(
            @RequestParam int limit
    ) {
        return eventService.getTopRatedEvents(limit);
    }

    @GetMapping("/search")
    public List<Event> searchEvents(
            @RequestParam(required = false) EventCategory category,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return eventService.searchEvents(category, startDate, endDate);
    }

}
