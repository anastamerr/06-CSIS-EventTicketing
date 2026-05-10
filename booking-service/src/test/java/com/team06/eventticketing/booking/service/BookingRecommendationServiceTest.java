package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.booking.dto.EventRecommendationCandidate;
import com.team06.eventticketing.booking.dto.EventRecommendationDTO;
import com.team06.eventticketing.booking.repository.AttendanceGraphRepository;
import com.team06.eventticketing.common.auth.JwtService;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingRecommendationServiceTest {

    @Mock
    private AttendanceGraphRepository attendanceGraphRepository;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private JwtService jwtService;

    @Mock
    private Claims claims;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private EventServiceClient eventServiceClient;

    private BookingRecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new BookingRecommendationService(
                attendanceGraphRepository,
                redisCacheService,
                new ObjectMapper(),
                jwtService,
                userServiceClient,
                eventServiceClient);
    }

    @Test
    void ownTokenGetsRecommendationsEnrichedThroughEventService() {
        when(jwtService.parseClaims("token")).thenReturn(claims);
        when(jwtService.extractUserId(claims)).thenReturn(1L);
        when(jwtService.extractRole(claims)).thenReturn("ATTENDEE");
        when(userServiceClient.getUser(1L)).thenReturn(new UserDTO(1L, "User", "user@example.com", "ATTENDEE"));
        when(redisCacheService.get(eq("booking-service::S3-F12::recommendations::1::5"), any(JavaType.class)))
                .thenReturn(null);
        when(attendanceGraphRepository.findRecommendations(1L, 5))
                .thenReturn(List.of(new EventRecommendationCandidate(30L, 1L)));
        LocalDateTime eventDate = LocalDateTime.of(2026, 4, 1, 20, 0);
        when(eventServiceClient.getEvent(30L)).thenReturn(new EventDTO(
                30L, "Concert", "Hall", eventDate, "CONCERT", "UPCOMING", 0.0, null));

        List<EventRecommendationDTO> result = recommendationService.getRecommendations(1L, null, "Bearer token");

        assertEquals(List.of(30L), result.stream().map(EventRecommendationDTO::getEventId).toList());
        assertEquals("Concert", result.get(0).getName());
    }
}
