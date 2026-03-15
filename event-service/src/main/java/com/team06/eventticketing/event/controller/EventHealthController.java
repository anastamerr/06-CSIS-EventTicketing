package com.team06.eventticketing.event.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventHealthController {

    @GetMapping("/api/events/health")
    public String health() {
        return "OK";
    }
}
