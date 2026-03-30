package com.team06.eventticketing.user.service;

import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FavoriteVenueService {

    private final FavoriteVenueRepository favoriteVenueRepository;
    private final UserRepository userRepository;

    public FavoriteVenueService(FavoriteVenueRepository favoriteVenueRepository, UserRepository userRepository) {
        this.favoriteVenueRepository = favoriteVenueRepository;
        this.userRepository = userRepository;
    }

    public FavoriteVenue addVenue(Long userId, FavoriteVenue venue) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        venue.setUser(user);
        return favoriteVenueRepository.save(venue);
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
        return favoriteVenueRepository.save(existingVenue);
    }

    public void deleteVenue(Long userId, Long venueId) {
        FavoriteVenue venue = getVenue(userId, venueId);
        favoriteVenueRepository.delete(venue);
    }
}
