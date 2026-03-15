package com.team06.eventticketing.ticket.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TicketHealthController {

    @GetMapping("/api/tickets/health")
    public String health() {
        return "OK";
    }
}
