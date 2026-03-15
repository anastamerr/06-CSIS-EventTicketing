package com.team06.eventticketing.sales.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SalesHealthController {

    @GetMapping("/api/sales/health")
    public String health() {
        return "OK";
    }
}
