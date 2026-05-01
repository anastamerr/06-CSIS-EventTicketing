package com.team06.eventticketing.booking.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.booking.dto.EventRecommendationCandidate;
import com.team06.eventticketing.booking.dto.EventRecommendationDTO;
import com.team06.eventticketing.booking.repository.AttendanceGraphRepository;
import com.team06.eventticketing.booking.repository.BookingRecommendationLookupRepository;
import com.team06.eventticketing.common.auth.JwtService;
import com.team06.eventticketing.common.cache.RedisCacheService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingRecommendationService {

    private static final int DEFAULT_LIMIT = 5;
    private static final long CACHE_TTL_SECONDS = 300L;

    private final AttendanceGraphRepository attendanceGraphRepository;
    private final BookingRecommendationLookupRepository lookupRepository;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    public BookingRecommendationService(
            AttendanceGraphRepository attendanceGraphRepository,
            BookingRecommendationLookupRepository lookupRepository,
            RedisCacheService redisCacheService,
            ObjectMapper objectMapper,
            JwtService jwtService) {
        this.attendanceGraphRepository = attendanceGraphRepository;
        this.lookupRepository = lookupRepository;
        this.redisCacheService = redisCacheService;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public List<EventRecommendationDTO> getRecommendations(Long userId, Integer requestedLimit, String authorization) {
        int limit = normalizeLimit(requestedLimit);
        Claims claims = parseClaims(authorization);
        Long callerUid = jwtService.extractUserId(claims);
        String callerRole = jwtService.extractRole(claims);

        if (!callerUid.equals(userId) && !"ADMIN".equalsIgnoreCase(callerRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!lookupRepository.userExists(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        String cacheKey = "booking-service::S3-F12::recommendations::" + userId + "::" + limit;
        JavaType cacheType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, EventRecommendationDTO.class);
        List<EventRecommendationDTO> cached = redisCacheService.get(cacheKey, cacheType);
        if (cached != null) {
            return cached;
        }

        List<EventRecommendationCandidate> candidates = attendanceGraphRepository.findRecommendations(userId, limit);
        List<EventRecommendationDTO> recommendations = enrichCandidates(candidates);
        redisCacheService.put(cacheKey, recommendations, CACHE_TTL_SECONDS);
        return recommendations;
    }

    private Claims parseClaims(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return jwtService.parseClaims(authorization.substring("Bearer ".length()));
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required", exception);
        }
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(0, requestedLimit);
    }

    private List<EventRecommendationDTO> enrichCandidates(List<EventRecommendationCandidate> candidates) {
        Map<Long, Long> scores = new LinkedHashMap<>();
        for (EventRecommendationCandidate candidate : candidates) {
            scores.put(candidate.eventId(), candidate.score());
        }

        List<EventRecommendationDTO> details = lookupRepository.findEventsByIds(scores.keySet().stream().toList(), scores);
        return details.stream()
                .sorted(Comparator
                        .comparingLong(EventRecommendationDTO::getScore)
                        .reversed()
                        .thenComparing(EventRecommendationDTO::getEventId))
                .toList();
    }
}
