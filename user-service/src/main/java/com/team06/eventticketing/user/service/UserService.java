package com.team06.eventticketing.user.service;

import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FavoriteVenueRepository favoriteVenueRepository;

    public UserService(UserRepository userRepository, FavoriteVenueRepository favoriteVenueRepository) {
        this.userRepository = userRepository;
        this.favoriteVenueRepository = favoriteVenueRepository;
    }

    public List<User> getAllUsers() { return userRepository.findAll(); }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User createUser(User user) { return userRepository.save(user); }

    public User updateUser(Long id, User user) {
        User existing = getUserById(id);
        existing.setName(user.getName());
        existing.setEmail(user.getEmail());
        existing.setPhone(user.getPhone());
        existing.setRole(user.getRole());
        existing.setPreferences(user.getPreferences());
        return userRepository.save(existing);
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

    public List<User> filterByPreference(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key and value must not be blank");
        }
        return userRepository.findByPreferenceKeyValue(key, value);
    }

    @Transactional
    public User setDefaultVenue(Long userId, Long venueId) {
        getUserById(userId);
        FavoriteVenue venue = favoriteVenueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        if (!venue.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue does not belong to this user");
        }

        List<FavoriteVenue> favoriteVenues = favoriteVenueRepository.findByUserIdOrderByIdAsc(userId);
        for (FavoriteVenue favoriteVenue : favoriteVenues) {
            favoriteVenue.setIsDefault(favoriteVenue.getId().equals(venueId));
        }
        favoriteVenueRepository.saveAll(favoriteVenues);

        return userRepository.findByIdWithFavoriteVenues(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
