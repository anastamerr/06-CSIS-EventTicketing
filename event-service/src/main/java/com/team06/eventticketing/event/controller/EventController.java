package com.team06.eventticketing.event.controller;

import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventStatus;
import com.team06.eventticketing.event.service.EventService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PutMapping("/{id}/details")
    public Event updateEventDetails(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return eventService.updateEventDetails(id, updates);
    }

    @GetMapping("/details/search")
    public List<Event> searchByDetails(@RequestParam String key, @RequestParam String value,
        @RequestParam(required = false) EventStatus status) {
        return eventService.findByDetailAttribute(key, value, status);
    }
}
