package com.team06.eventticketing.user.controller;

import com.team06.eventticketing.common.cache.CachedDetail;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.service.FavoriteVenueService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/venues")
public class FavoriteVenueController {

    private final FavoriteVenueService favoriteVenueService;

    public FavoriteVenueController(FavoriteVenueService favoriteVenueService) {
        this.favoriteVenueService = favoriteVenueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteVenue addVenue(@PathVariable Long userId, @RequestBody FavoriteVenue venue) {
        return favoriteVenueService.addVenue(userId, venue);
    }

    @GetMapping
    public List<FavoriteVenue> getVenues(@PathVariable Long userId) {
        return favoriteVenueService.getVenues(userId);
    }

    @GetMapping("/{venueId}")
    @CachedDetail(service = "user-service", entity = "favorite-venue", key = "#venueId", ttlSeconds = 900)
    public FavoriteVenue getVenue(@PathVariable Long userId, @PathVariable Long venueId) {
        return favoriteVenueService.getVenue(userId, venueId);
    }

    @PutMapping("/{venueId}")
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::favorite-venue::' + #venueId", "'user-service::user::' + #userId"})
    public FavoriteVenue updateVenue(
            @PathVariable Long userId,
            @PathVariable Long venueId,
            @RequestBody FavoriteVenue venue
    ) {
        return favoriteVenueService.updateVenue(userId, venueId, venue);
    }

    @DeleteMapping("/{venueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @InvalidateServiceCaches(
            service = "user-service",
            featurePrefix = "S1-",
            detailKeys = {"'user-service::favorite-venue::' + #venueId", "'user-service::user::' + #userId"})
    public void deleteVenue(@PathVariable Long userId, @PathVariable Long venueId) {
        favoriteVenueService.deleteVenue(userId, venueId);
    }
}
