package com.team06.eventticketing.event.controller;

import com.team06.eventticketing.common.cache.CachedDetail;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.event.model.EventSession;
import com.team06.eventticketing.event.service.EventSessionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.team06.eventticketing.event.dto.AvgCapacityDTO;

@RestController
@RequestMapping("/api/events/{eventId}/sessions")
public class EventSessionController {

    private final EventSessionService eventSessionService;

    public EventSessionController(EventSessionService eventSessionService) {
        this.eventSessionService = eventSessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventSession createSession(@PathVariable Long eventId, @RequestBody EventSession session) {
        return eventSessionService.createSession(eventId, session);
    }

    @GetMapping
    public List<EventSession> getSessions(@PathVariable Long eventId) {
        return eventSessionService.getSessions(eventId);
    }
    @GetMapping("/avg-capacity")
    public AvgCapacityDTO getAverageCapacity(@PathVariable Long eventId) {
        return eventSessionService.getAverageCapacity(eventId);
    }
    @GetMapping("/{sessionId}")
    @CachedDetail(service = "event-service", entity = "event-session", key = "#sessionId", ttlSeconds = 900)
    public EventSession getSession(@PathVariable Long eventId, @PathVariable Long sessionId) {
        return eventSessionService.getSession(eventId, sessionId);
    }

    @PutMapping("/{sessionId}")
    @InvalidateServiceCaches(
            service = "event-service",
            featurePrefix = "S2-",
            detailKeys = {"'event-service::event-session::' + #sessionId", "'event-service::event::' + #eventId"})
    public EventSession updateSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody EventSession session
    ) {
        return eventSessionService.updateSession(eventId, sessionId, session);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @InvalidateServiceCaches(
            service = "event-service",
            featurePrefix = "S2-",
            detailKeys = {"'event-service::event-session::' + #sessionId", "'event-service::event::' + #eventId"})
    public void deleteSession(@PathVariable Long eventId, @PathVariable Long sessionId) {
        eventSessionService.deleteSession(eventId, sessionId);
    }
}
