package com.team06.eventticketing.user.controller;

import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/preferences/search")
    public List<User> filterByPreference(@RequestParam String key, @RequestParam String value) {
        return userService.filterByPreference(key, value);
    }
}