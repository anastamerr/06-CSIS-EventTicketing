package com.team06.eventticketing.user.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.common.auth.JwtService;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.user.adapter.MongoDocumentAdapter;
import com.team06.eventticketing.user.dto.UserActivityEventDTO;
import com.team06.eventticketing.user.dto.UserActivityFeedResponse;
import com.team06.eventticketing.user.repository.AuthEventRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserActivityService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final long CACHE_TTL_SECONDS = 300;

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthEventRepository authEventRepository;
    private final MongoDocumentAdapter mongoDocumentAdapter;
    private final RedisCacheService redisCacheService;
    private final JavaType responseType;

    public UserActivityService(
            JwtService jwtService,
            UserRepository userRepository,
            AuthEventRepository authEventRepository,
            MongoDocumentAdapter mongoDocumentAdapter,
            RedisCacheService redisCacheService,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authEventRepository = authEventRepository;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
        this.redisCacheService = redisCacheService;
        this.responseType = objectMapper.getTypeFactory().constructType(UserActivityFeedResponse.class);
    }

    public UserActivityFeedResponse getActivityFeed(Long targetUserId, Integer page, Integer size, String authHeader) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        Caller caller = resolveCaller(authHeader);

        if (!targetUserId.equals(caller.userId()) && !"ADMIN".equals(caller.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        String cacheKey = "user-service::S1-F12::" + targetUserId + "::" + normalizedPage + "::" + normalizedSize;
        UserActivityFeedResponse cached = redisCacheService.get(cacheKey, responseType);
        if (cached != null) {
            return cached;
        }

        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "timestamp"));
        var authEvents = authEventRepository.findByUserId(targetUserId, pageable);
        List<UserActivityEventDTO> content = authEvents.getContent().stream()
                .map(mongoDocumentAdapter::adapt)
                .toList();
        UserActivityFeedResponse response = new UserActivityFeedResponse(
                content,
                normalizedPage,
                normalizedSize,
                authEvents.getTotalElements());
        redisCacheService.put(cacheKey, response, CACHE_TTL_SECONDS);
        return response;
    }

    private Caller resolveCaller(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        try {
            Claims claims = jwtService.parseClaims(authHeader.substring(7));
            return new Caller(jwtService.extractUserId(claims), jwtService.extractRole(claims));
        } catch (RuntimeException exception) {
            return resolveCallerFromSecurityContext();
        }
    }

    private Caller resolveCallerFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token");
        }
        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token"));
        String role = authentication.getAuthorities().stream()
                .map(Object::toString)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(user.getRole().name());
        return new Caller(user.getId(), role);
    }

    private int normalizePage(Integer page) {
        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        if (normalizedPage < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        return normalizedPage;
    }

    private int normalizeSize(Integer size) {
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        if (normalizedSize <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be greater than zero");
        }
        return Math.min(normalizedSize, MAX_SIZE);
    }

    private record Caller(Long userId, String role) {
    }
}
