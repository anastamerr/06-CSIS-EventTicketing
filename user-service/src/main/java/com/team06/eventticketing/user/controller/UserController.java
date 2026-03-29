package com.team06.eventticketing.user.controller;

import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody User user) { return userService.createUser(user); }

    @GetMapping
    public List<User> getAllUsers() { return userService.getAllUsers(); }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) { return userService.getUserById(id); }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User user) { return userService.updateUser(id, user); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) { userService.deleteUser(id); }

    @GetMapping("/preferences/search")
    public List<User> filterByPreference(@RequestParam String key, @RequestParam String value) {
        return userService.filterByPreference(key, value);
    }
}