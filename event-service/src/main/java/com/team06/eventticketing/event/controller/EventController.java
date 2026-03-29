package com.team06.eventticketing.event.controller;

import com.team06.eventticketing.event.dto.VerifyEventSessionRequest;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.service.EventService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PutMapping("/{eventId}/sessions/{sessionId}/verify")
    public Event verifyEventSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @RequestBody VerifyEventSessionRequest request
    ) {
        return eventService.verifyEventSession(eventId, sessionId, request);
    }
}
