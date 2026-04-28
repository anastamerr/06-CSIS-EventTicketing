package com.team06.eventticketing.user.service;

import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class FavoriteVenueService {

    private final FavoriteVenueRepository favoriteVenueRepository;
    private final UserRepository userRepository;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public FavoriteVenueService(
            FavoriteVenueRepository favoriteVenueRepository,
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.favoriteVenueRepository = favoriteVenueRepository;
        this.userRepository = userRepository;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public FavoriteVenueService(FavoriteVenueRepository favoriteVenueRepository, UserRepository userRepository) {
        this.favoriteVenueRepository = favoriteVenueRepository;
        this.userRepository = userRepository;
    }

    public FavoriteVenue addVenue(Long userId, FavoriteVenue venue) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        venue.setUser(user);
        FavoriteVenue saved = favoriteVenueRepository.save(venue);
        notifyObservers("FAVORITE_VENUE_CREATED", Map.of(
                "userId", userId,
                "details", buildVenueDetails(saved)));
        return saved;
    }

    public List<FavoriteVenue> getVenues(Long userId) {
        return favoriteVenueRepository.findByUserId(userId);
    }

    public FavoriteVenue getVenue(Long userId, Long venueId) {
        FavoriteVenue venue = favoriteVenueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        if (!venue.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue does not belong to this user");
        }
        return venue;
    }

    public FavoriteVenue updateVenue(Long userId, Long venueId, FavoriteVenue updatedVenue) {
        FavoriteVenue existingVenue = getVenue(userId, venueId);
        existingVenue.setLabel(updatedVenue.getLabel());
        existingVenue.setVenueName(updatedVenue.getVenueName());
        existingVenue.setLocation(updatedVenue.getLocation());
        existingVenue.setCapacity(updatedVenue.getCapacity());
        existingVenue.setIsDefault(updatedVenue.getIsDefault());
        existingVenue.setMetadata(updatedVenue.getMetadata());
        FavoriteVenue saved = favoriteVenueRepository.save(existingVenue);
        notifyObservers("FAVORITE_VENUE_UPDATED", Map.of(
                "userId", userId,
                "details", buildVenueDetails(saved)));
        return saved;
    }

    public void deleteVenue(Long userId, Long venueId) {
        FavoriteVenue venue = getVenue(userId, venueId);
        notifyObservers("FAVORITE_VENUE_DELETED", Map.of(
                "userId", userId,
                "details", buildVenueDetails(venue)));
        favoriteVenueRepository.delete(venue);
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

    private Map<String, Object> buildVenueDetails(FavoriteVenue venue) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("venueId", venue.getId());
        details.put("venueName", venue.getVenueName());
        details.put("label", venue.getLabel());
        details.put("isDefault", venue.getIsDefault());
        return details;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.AUTH, "auth_events"));
        }
    }
}
