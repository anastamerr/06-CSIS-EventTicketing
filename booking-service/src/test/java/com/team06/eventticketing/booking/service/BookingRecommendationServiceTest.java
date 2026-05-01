package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.booking.dto.EventRecommendationCandidate;
import com.team06.eventticketing.booking.dto.EventRecommendationDTO;
import com.team06.eventticketing.booking.repository.AttendanceGraphRepository;
import com.team06.eventticketing.booking.repository.BookingRecommendationLookupRepository;
import com.team06.eventticketing.common.auth.JwtService;
import com.team06.eventticketing.common.cache.RedisCacheService;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BookingRecommendationServiceTest {

    @Mock
    private AttendanceGraphRepository attendanceGraphRepository;

    @Mock
    private BookingRecommendationLookupRepository lookupRepository;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private JwtService jwtService;

    @Mock
    private Claims claims;

    private BookingRecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new BookingRecommendationService(
                attendanceGraphRepository,
                lookupRepository,
                redisCacheService,
                new ObjectMapper(),
                jwtService);
    }

    @Test
    void ownTokenGetsDefaultLimitRecommendationsSortedByScoreAndEventId() {
        when(jwtService.parseClaims("token")).thenReturn(claims);
        when(jwtService.extractUserId(claims)).thenReturn(1L);
        when(jwtService.extractRole(claims)).thenReturn("ATTENDEE");
        when(lookupRepository.userExists(1L)).thenReturn(true);
        when(redisCacheService.get(eq("booking-service::S3-F12::recommendations::1::5"), any(JavaType.class)))
                .thenReturn(null);
        when(attendanceGraphRepository.findRecommendations(1L, 5))
                .thenReturn(List.of(new EventRecommendationCandidate(30L, 1L), new EventRecommendationCandidate(40L, 2L)));
        when(lookupRepository.findEventsByIds(eq(List.of(30L, 40L)), any()))
                .thenReturn(List.of(
                        recommendation(40L, "Theater", 2L),
                        recommendation(30L, "Concert", 1L)));

        List<EventRecommendationDTO> result = recommendationService.getRecommendations(1L, null, "Bearer token");

        assertEquals(List.of(40L, 30L), result.stream().map(EventRecommendationDTO::getEventId).toList());
        verify(redisCacheService).put("booking-service::S3-F12::recommendations::1::5", result, 300L);
    }

    @Test
    void otherAttendeeTokenIsForbiddenButAdminBypassesOwnership() {
        when(jwtService.parseClaims("user-token")).thenReturn(claims);
        when(jwtService.extractUserId(claims)).thenReturn(2L);
        when(jwtService.extractRole(claims)).thenReturn("ATTENDEE");

        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> recommendationService.getRecommendations(1L, 5, "Bearer user-token"));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        verify(lookupRepository, never()).userExists(1L);

        when(jwtService.parseClaims("admin-token")).thenReturn(claims);
        when(jwtService.extractUserId(claims)).thenReturn(2L);
        when(jwtService.extractRole(claims)).thenReturn("ADMIN");
        when(lookupRepository.userExists(1L)).thenReturn(true);
        when(redisCacheService.get(eq("booking-service::S3-F12::recommendations::1::5"), any(JavaType.class)))
                .thenReturn(List.of());

        assertEquals(List.of(), recommendationService.getRecommendations(1L, 5, "Bearer admin-token"));
    }

    @Test
    void recommendationsRejectMissingTokenAndUnknownUser() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> recommendationService.getRecommendations(1L, 5, null)).getStatusCode());

        when(jwtService.parseClaims("admin-token")).thenReturn(claims);
        when(jwtService.extractUserId(claims)).thenReturn(99L);
        when(jwtService.extractRole(claims)).thenReturn("ADMIN");
        when(lookupRepository.userExists(999L)).thenReturn(false);

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> recommendationService.getRecommendations(999L, 5, "Bearer admin-token")).getStatusCode());
    }

    private EventRecommendationDTO recommendation(Long eventId, String name, long score) {
        return new EventRecommendationDTO(eventId, name, "CONCERT", LocalDateTime.of(2026, 4, 1, 20, 0), score);
    }
}
