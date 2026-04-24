package com.team06.eventticketing.user.service;

import com.team06.eventticketing.common.auth.JwtService;
import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.user.adapter.TopAttendeeAdapter;
import com.team06.eventticketing.user.adapter.UserBookingSummaryAdapter;
import com.team06.eventticketing.user.dto.AuthResponse;
import com.team06.eventticketing.user.dto.LoginRequest;
import com.team06.eventticketing.user.dto.RegisterRequest;
import com.team06.eventticketing.user.dto.TopAttendeeDTO;
import com.team06.eventticketing.user.dto.UpdateUserRoleRequest;
import com.team06.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.user.dto.UserProfileDTO;
import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.model.UserRole;
import com.team06.eventticketing.user.model.UserStatus;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FavoriteVenueRepository favoriteVenueRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserBookingSummaryAdapter userBookingSummaryAdapter;
    private final TopAttendeeAdapter topAttendeeAdapter;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    public UserService(
            UserRepository userRepository,
            FavoriteVenueRepository favoriteVenueRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserBookingSummaryAdapter userBookingSummaryAdapter,
            TopAttendeeAdapter topAttendeeAdapter,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.userRepository = userRepository;
        this.favoriteVenueRepository = favoriteVenueRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userBookingSummaryAdapter = userBookingSummaryAdapter;
        this.topAttendeeAdapter = topAttendeeAdapter;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public UserService(UserRepository userRepository, FavoriteVenueRepository favoriteVenueRepository) {
        this(
                userRepository,
                favoriteVenueRepository,
                new BCryptPasswordEncoder(),
                new JwtService(),
                new UserBookingSummaryAdapter(),
                new TopAttendeeAdapter(),
                null,
                null);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> searchUsers(String name, String email, String role) {
        return userRepository.searchByOptionalNameEmailRole(
                normalizeFilter(name),
                normalizeFilter(email),
                normalizeFilter(role));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User createUser(User user) {
        prepareUserForSave(user, null);
        validateUniqueContactFields(user, null);
        User saved = userRepository.save(user);
        notifyObservers("USER_CREATED", Map.of(
                "userId", saved.getId(),
                "details", buildUserDetails(saved)));
        return saved;
    }

    public User updateUser(Long id, User user) {
        User existing = getUserById(id);
        prepareUserForSave(user, existing);
        validateUniqueContactFields(user, existing);
        existing.setName(user.getName() == null ? existing.getName() : user.getName());
        existing.setEmail(user.getEmail() == null ? existing.getEmail() : user.getEmail());
        existing.setPhone(user.getPhone() == null ? existing.getPhone() : user.getPhone());
        existing.setStatus(user.getStatus() == null ? existing.getStatus() : user.getStatus());
        existing.setPreferences(user.getPreferences() == null ? existing.getPreferences() : user.getPreferences());
        if (user.getPassword() != null) {
            existing.setPassword(user.getPassword());
        }
        User saved = userRepository.save(existing);
        notifyObservers("USER_UPDATED", Map.of(
                "userId", saved.getId(),
                "details", buildUserDetails(saved)));
        return saved;
    }

    @Transactional
    public User deactivateUser(Long id) {
        User user = getUserById(id);
        if (userRepository.existsActiveBookingsByUserId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has active bookings");
        }
        user.setStatus(UserStatus.DEACTIVATED);
        User saved = userRepository.save(user);
        notifyObservers("USER_DEACTIVATED", Map.of(
                "userId", saved.getId(),
                "details", buildUserDetails(saved)));
        return saved;
    }

    public void deleteUser(Long id) {
        User user = getUserById(id);
        notifyObservers("USER_DELETED", Map.of(
                "userId", user.getId(),
                "details", buildUserDetails(user)));
        userRepository.deleteById(id);
    }

    public User updatePreferences(Long id, Map<String, Object> incoming) {
        User user = getUserById(id);
        Map<String, Object> existing = user.getPreferences();
        if (existing == null) {
            existing = new java.util.LinkedHashMap<>();
        }
        existing.putAll(incoming);
        user.setPreferences(existing);
        User saved = userRepository.save(user);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("preferences", saved.getPreferences());
        details.put("email", saved.getEmail());
        notifyObservers("USER_UPDATED", Map.of(
                "userId", saved.getId(),
                "details", details));
        return saved;
    }

    public UserProfileDTO getUserProfile(Long id) {
        User user = userRepository.findByIdWithFavoriteVenues(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<UserProfileDTO.VenueDTO> venueDTOs = user.getFavoriteVenues().stream()
                .map(v -> new UserProfileDTO.VenueDTO(
                        v.getId(),
                        v.getLabel(),
                        v.getVenueName(),
                        v.getLocation(),
                        v.getCapacity(),
                        v.getIsDefault(),
                        v.getMetadata()))
                .collect(Collectors.toList());

        return UserProfileDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .preferences(user.getPreferences())
                .favoriteVenues(venueDTOs)
                .build();
    }

    @Transactional(readOnly = true)
    public UserBookingSummaryDTO getUserBookingSummary(Long id) {
        User user = getUserById(id);
        List<Object[]> summaryRows = userRepository.findBookingSummaryByUserId(id);

        if (summaryRows.isEmpty()) {
            return userBookingSummaryAdapter.empty(user);
        }

        return userBookingSummaryAdapter.adapt(user, summaryRows.getFirst());
    }

    public List<User> getUsersByFavoriteCategoryAndMinBookings(String category, int minBookings) {
        if (category == null || category.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category must not be blank");
        }
        if (minBookings < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minBookings must not be negative");
        }
        return userRepository.findByCategoryAndMinCompletedBookings(category, minBookings);
    }

    public List<User> filterByPreference(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key and value must not be blank");
        }
        return userRepository.findByPreferenceKeyValue(key, value);
    }

    @Transactional(readOnly = true)
    public List<TopAttendeeDTO> getTopAttendeesBySpending(LocalDate startDate, LocalDate endDate, int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be greater than zero");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        LocalDateTime startInclusive = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        return userRepository.findTopAttendeesBySpending(startInclusive, endExclusive, PageRequest.of(0, limit))
                .stream()
                .map(topAttendeeAdapter::adapt)
                .toList();
    }

    @Transactional
    public User setDefaultVenue(Long userId, Long venueId) {
        getUserById(userId);
        FavoriteVenue venue = favoriteVenueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        if (!venue.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue does not belong to this user");
        }

        favoriteVenueRepository.clearDefaultsForUser(userId);
        venue.setIsDefault(Boolean.TRUE);
        favoriteVenueRepository.save(venue);

        User saved = userRepository.findByIdWithFavoriteVenues(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        notifyObservers("DEFAULT_VENUE_SET", Map.of(
                "userId", saved.getId(),
                "details", Map.of(
                        "venueId", venueId,
                        "favoriteVenueCount", saved.getFavoriteVenues().size())));
        return saved;
    }

    @Transactional
    public User updateRole(Long id, UpdateUserRoleRequest request) {
        if (request == null || request.getRole() == null || request.getRole().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }

        User user = getUserById(id);
        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role");
        }
        user.setRole(role);
        User saved = userRepository.save(user);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("role", saved.getRole().name());
        details.put("email", saved.getEmail());
        notifyObservers("ROLE_CHANGED", Map.of(
                "userId", saved.getId(),
                "details", details));
        return saved;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setPhone(request.getPhone());
        user.setPreferences(request.getPreferences());
        user.setRole(UserRole.ATTENDEE);

        User saved = createUser(user);
        notifyObservers("REGISTERED", Map.of(
                "userId", saved.getId(),
                "details", buildUserDetails(saved)));
        return new AuthResponse(
                jwtService.generateToken(saved.getId(), saved.getEmail(), saved.getRole().name()),
                saved.getId(),
                saved.getEmail(),
                saved.getRole().name(),
                saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
        }
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        notifyObservers("LOGGED_IN", Map.of(
                "userId", user.getId(),
                "details", buildUserDetails(user)));
        return new AuthResponse(
                jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name()),
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user);
    }

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String action, Object payload) {
        observers.forEach(observer -> observer.onEvent(action, payload));
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void prepareUserForSave(User candidate, User existing) {
        if (candidate.getPassword() != null && candidate.getPassword().isBlank()) {
            candidate.setPassword(null);
        }
        if (candidate.getPreferences() == null) {
            candidate.setPreferences(existing == null ? new LinkedHashMap<>() : existing.getPreferences());
        }
        if (existing == null) {
            candidate.setRole(UserRole.ATTENDEE);
        } else if (candidate.getRole() == null) {
            candidate.setRole(existing.getRole());
        }
        if (candidate.getStatus() == null && existing == null) {
            candidate.setStatus(UserStatus.ACTIVE);
        }
        if (candidate.getPassword() != null) {
            candidate.setPassword(ensurePasswordHash(candidate.getPassword()));
        }
    }

    private void validateUniqueContactFields(User candidate, User existing) {
        String email = candidate.getEmail();
        if (email != null) {
            userRepository.findByEmail(email)
                    .filter(found -> existing == null || !found.getId().equals(existing.getId()))
                    .ifPresent(found -> {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
                    });
        }

        String phone = candidate.getPhone();
        if (phone != null) {
            userRepository.findByPhone(phone)
                    .filter(found -> existing == null || !found.getId().equals(existing.getId()))
                    .ifPresent(found -> {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone already exists");
                    });
        }
    }

    private String ensurePasswordHash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return rawPassword;
        }
        if (rawPassword.matches("^\\$2[aby]\\$\\d\\d\\$.{53}$")) {
            return rawPassword;
        }
        return passwordEncoder.encode(rawPassword);
    }

    private Map<String, Object> buildUserDetails(User user) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("email", user.getEmail());
        details.put("name", user.getName());
        details.put("role", user.getRole() == null ? null : user.getRole().name());
        return details;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.AUTH, "auth_events"));
        }
    }
}
