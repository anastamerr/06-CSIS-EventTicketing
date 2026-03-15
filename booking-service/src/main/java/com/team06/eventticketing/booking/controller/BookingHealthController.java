package com.team06.eventticketing.booking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingHealthController {

    @GetMapping("/api/bookings/health")
    public String health() {
        return "OK";
    }
}
