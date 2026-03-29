package com.team06.eventticketing.user.controller;

import com.team06.eventticketing.user.dto.TopAttendeeDTO;
import com.team06.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.user.dto.UserProfileDTO;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Validated
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

    @GetMapping("/reports/top-attendees")
    public List<TopAttendeeDTO> getTopAttendeesBySpending(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam @Min(1) int limit) {
        return userService.getTopAttendeesBySpending(startDate, endDate, limit);
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) { return userService.getUserById(id); }

    @GetMapping("/{id}/profile")
    public UserProfileDTO getUserProfile(@PathVariable Long id) {
        return userService.getUserProfile(id);
    }

    @GetMapping("/{id}/booking-summary")
    public UserBookingSummaryDTO getUserBookingSummary(@PathVariable Long id) {
        return userService.getUserBookingSummary(id);
    }

    @PutMapping("/{userId}/venues/{venueId}/default")
    public User setDefaultVenue(@PathVariable Long userId, @PathVariable Long venueId) {
        return userService.setDefaultVenue(userId, venueId);
    }

    @PutMapping("/{id}/preferences")
    public User updatePreferences(@PathVariable Long id, @RequestBody Map<String, Object> preferences) {
        return userService.updatePreferences(id, preferences);
    }

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
