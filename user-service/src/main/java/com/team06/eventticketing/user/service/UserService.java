package com.team06.eventticketing.user.service;

import com.team06.eventticketing.user.dto.TopAttendeeDTO;
import com.team06.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.user.dto.UserProfileDTO;
import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.model.UserStatus;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FavoriteVenueRepository favoriteVenueRepository;

    public UserService(UserRepository userRepository, FavoriteVenueRepository favoriteVenueRepository) {
        this.userRepository = userRepository;
        this.favoriteVenueRepository = favoriteVenueRepository;
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
        return userRepository.save(user);
    }

    public User updateUser(Long id, User user) {
        User existing = getUserById(id);
        prepareUserForSave(user, existing);
        validateUniqueContactFields(user, existing);
        existing.setName(user.getName() == null ? existing.getName() : user.getName());
        existing.setEmail(user.getEmail() == null ? existing.getEmail() : user.getEmail());
        existing.setPhone(user.getPhone() == null ? existing.getPhone() : user.getPhone());
        existing.setRole(user.getRole() == null ? existing.getRole() : user.getRole());
        existing.setStatus(user.getStatus() == null ? existing.getStatus() : user.getStatus());
        existing.setPreferences(user.getPreferences() == null ? existing.getPreferences() : user.getPreferences());
        if (user.getPassword() != null) {
            existing.setPassword(user.getPassword());
        }
        return userRepository.save(existing);
    }

    @Transactional
    public User deactivateUser(Long id) {
        User user = getUserById(id);
        if (userRepository.existsActiveBookingsByUserId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has active bookings");
        }
        user.setStatus(UserStatus.DEACTIVATED);
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        getUserById(id);
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
        return userRepository.save(user);
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

        return new UserProfileDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getPreferences(),
                venueDTOs);
    }

    @Transactional(readOnly = true)
    public UserBookingSummaryDTO getUserBookingSummary(Long id) {
        User user = getUserById(id);
        List<Object[]> summaryRows = userRepository.findBookingSummaryByUserId(id);

        if (summaryRows.isEmpty()) {
            return emptyBookingSummary(user);
        }

        return mapUserBookingSummaryRow(user, summaryRows.getFirst());
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
                .map(this::mapTopAttendeeRow)
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

        return userRepository.findByIdWithFavoriteVenues(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private TopAttendeeDTO mapTopAttendeeRow(Object[] row) {
        row = unwrapRow(row);
        return new TopAttendeeDTO(
                toLong(row[0]),
                row[1] == null ? null : row[1].toString(),
                toBigDecimal(row[2]),
                toLong(row[3]));
    }

    private UserBookingSummaryDTO mapUserBookingSummaryRow(User user, Object[] row) {
        row = unwrapRow(row);
        Object userId = valueAt(row, 0);
        Object name = valueAt(row, 1);

        return new UserBookingSummaryDTO(
                userId == null ? user.getId() : toLong(userId),
                name == null ? user.getName() : name.toString(),
                toLong(valueAt(row, 2)),
                toLong(valueAt(row, 3)),
                toLong(valueAt(row, 4)),
                toBigDecimal(valueAt(row, 5)),
                toBigDecimal(valueAt(row, 6)));
    }

    private UserBookingSummaryDTO emptyBookingSummary(User user) {
        return new UserBookingSummaryDTO(
                user.getId(),
                user.getName(),
                0L,
                0L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private Object valueAt(Object[] row, int index) {
        row = unwrapRow(row);
        if (row == null || index >= row.length) {
            return null;
        }
        return row[index];
    }

    private Object[] unwrapRow(Object[] row) {
        Object[] current = row;
        while (current != null && current.length == 1 && current[0] instanceof Object[] nestedRow) {
            current = nestedRow;
        }
        return current;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
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
        if (candidate.getStatus() == null && existing == null) {
            candidate.setStatus(UserStatus.ACTIVE);
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
}
