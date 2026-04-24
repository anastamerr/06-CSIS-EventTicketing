package com.team06.eventticketing.user.controller;

import com.team06.eventticketing.common.cache.CachedDetail;
import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.user.dto.TopAttendeeDTO;
import com.team06.eventticketing.user.dto.UpdateUserRoleRequest;
import com.team06.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.user.dto.UserProfileDTO;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.service.UserService;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/search")
    @CachedFeature(service = "user-service", featureId = "S1-F1", ttlSeconds = 300)
    public List<User> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role) {
        return userService.searchUsers(name, email, role);
    }

    @GetMapping("/reports/top-attendees")
    @CachedFeature(service = "user-service", featureId = "S1-F6", ttlSeconds = 600)
    public List<TopAttendeeDTO> getTopAttendeesBySpending(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam @Min(1) int limit) {
        return userService.getTopAttendeesBySpending(startDate, endDate, limit);
    }

    @GetMapping("/{id}")
    @CachedDetail(service = "user-service", entity = "user", key = "#id", ttlSeconds = 900)
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/{id}/profile")
    @CachedFeature(service = "user-service", featureId = "S1-F8", ttlSeconds = 900)
    public UserProfileDTO getUserProfile(@PathVariable Long id) {
        return userService.getUserProfile(id);
    }

    @GetMapping("/{id}/booking-summary")
    @CachedFeature(service = "user-service", featureId = "S1-F3", ttlSeconds = 600)
    public UserBookingSummaryDTO getUserBookingSummary(@PathVariable Long id) {
        return userService.getUserBookingSummary(id);
    }

    @GetMapping("/{id}/bookings/summary")
    @CachedFeature(service = "user-service", featureId = "S1-F3", ttlSeconds = 600)
    public UserBookingSummaryDTO getUserBookingsSummary(@PathVariable Long id) {
        return userService.getUserBookingSummary(id);
    }

    @PutMapping("/{userId}/venues/{venueId}/default")
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::user::' + #userId", "'user-service::favorite-venue::' + #venueId"})
    public User setDefaultVenue(@PathVariable Long userId, @PathVariable Long venueId) {
        return userService.setDefaultVenue(userId, venueId);
    }

    @PutMapping("/{id}/preferences")
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::user::' + #id"})
    public User updatePreferences(@PathVariable Long id, @RequestBody Map<String, Object> preferences) {
        return userService.updatePreferences(id, preferences);
    }

    @PatchMapping("/{id}/preferences")
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::user::' + #id"})
    public User patchPreferences(@PathVariable Long id, @RequestBody Map<String, Object> preferences) {
        return userService.updatePreferences(id, preferences);
    }

    @PutMapping("/{id}")
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::user::' + #id"})
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        return userService.updateUser(id, user);
    }

    @PutMapping("/{id}/deactivate")
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::user::' + #id"})
    public User deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }

    @PutMapping("/{id}/role")
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::user::' + #id"})
    public User updateRole(@PathVariable Long id, @RequestBody UpdateUserRoleRequest request) {
        return userService.updateRole(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::user::' + #id"})
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    @GetMapping("/preferences/search")
    @CachedFeature(service = "user-service", featureId = "S1-F9", ttlSeconds = 600)
    public List<User> filterByPreference(@RequestParam String key, @RequestParam String value) {
        return userService.filterByPreference(key, value);
    }

    @GetMapping("/preferences/category")
    @CachedFeature(service = "user-service", featureId = "S1-F5", ttlSeconds = 300)
    public List<User> getUsersByFavoriteCategory(
            @RequestParam String category,
            @RequestParam int minBookings) {
        return userService.getUsersByFavoriteCategoryAndMinBookings(category, minBookings);
    }

    @GetMapping("/category/{category}")
    @CachedFeature(service = "user-service", featureId = "S1-F5", ttlSeconds = 300)
    public List<User> getUsersByFavoriteCategoryPath(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int minBookings) {
        return userService.getUsersByFavoriteCategoryAndMinBookings(category, minBookings);
    }
}
