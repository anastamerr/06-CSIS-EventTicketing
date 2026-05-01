package com.team06.eventticketing.booking.controller;

import com.team06.eventticketing.booking.dto.EventRecommendationDTO;
import com.team06.eventticketing.booking.service.BookingRecommendationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingRecommendationController {

    private final BookingRecommendationService recommendationService;

    public BookingRecommendationController(BookingRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendations")
    public List<EventRecommendationDTO> getRecommendations(
            @RequestParam Long userId,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return recommendationService.getRecommendations(userId, limit, authorization);
    }
}
