package com.team06.eventticketing.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserHealthController {

    @GetMapping("/api/users/health")
    public String health() {
        return "OK";
    }
}
