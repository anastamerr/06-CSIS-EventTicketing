package com.team06.eventticketing.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.user.dto.UserProfileDTO;
import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FavoriteVenueRepository favoriteVenueRepository;

    @Captor
    private ArgumentCaptor<List<FavoriteVenue>> venuesCaptor;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, favoriteVenueRepository);
    }

    @Test
    void getUserProfileThrowsNotFoundForUnknownUser() {
        when(userRepository.findByIdWithFavoriteVenues(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getUserProfile(999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updatePreferencesThrowsNotFoundForUnknownUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.updatePreferences(999L, Map.of("language", "en")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updatePreferencesMergesIncomingKeepsExistingAndOverwritesMatchingKeys() {
        User user = new User();
        user.setId(1L);
        Map<String, Object> existing = new java.util.LinkedHashMap<>();
        existing.put("language", "en");
        existing.put("favoriteCategory", "CONCERT");
        user.setPreferences(existing);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        Map<String, Object> incoming = Map.of("favoriteCategory", "SPORTS", "ticketTier", "VIP");
        User result = userService.updatePreferences(1L, incoming);

        assertEquals("en", result.getPreferences().get("language"));
        assertEquals("SPORTS", result.getPreferences().get("favoriteCategory"));
        assertEquals("VIP", result.getPreferences().get("ticketTier"));
    }

    @Test
    void getUserProfileReturnsCorrectDTOWithVenues() {
        User user = new User();
        user.setId(1L);
        user.setName("Ahmed");
        user.setEmail("ahmed@mail.com");
        user.setPhone("+201011111111");
        user.setPreferences(Map.of("language", "en"));

        FavoriteVenue v1 = venue(11L, user, true);
        v1.setLabel("Go-To");
        v1.setVenueName("Cairo Opera");
        v1.setLocation("Zamalek");
        FavoriteVenue v2 = venue(12L, user, false);
        v2.setLabel("Weekend");
        v2.setVenueName("Cairo Stadium");
        v2.setLocation("Nasr City");
        FavoriteVenue v3 = venue(13L, user, false);
        v3.setLabel("Home");
        v3.setVenueName("CFC Arena");
        v3.setLocation("New Cairo");

        user.setFavoriteVenues(new ArrayList<>(List.of(v1, v2, v3)));

        when(userRepository.findByIdWithFavoriteVenues(1L)).thenReturn(Optional.of(user));

        UserProfileDTO dto = userService.getUserProfile(1L);

        assertEquals(1L, dto.getUserId());
        assertEquals("Ahmed", dto.getName());
        assertEquals("ahmed@mail.com", dto.getEmail());
        assertEquals(3, dto.getTotalFavoriteVenues());
        assertEquals(3, dto.getFavoriteVenues().size());
        assertEquals("Cairo Opera", dto.getFavoriteVenues().get(0).getVenueName());
        assertEquals(Boolean.TRUE, dto.getFavoriteVenues().get(0).getIsDefault());
    }

    @Test
    void getUserProfileWithNoVenuesReturnsEmptyListAndZeroCount() {
        User user = new User();
        user.setId(2L);
        user.setName("Sara");
        user.setEmail("sara@mail.com");
        user.setPhone("+201022222222");
        user.setPreferences(Map.of());
        user.setFavoriteVenues(new ArrayList<>());

        when(userRepository.findByIdWithFavoriteVenues(2L)).thenReturn(Optional.of(user));

        UserProfileDTO dto = userService.getUserProfile(2L);

        assertEquals(0, dto.getTotalFavoriteVenues());
        assertEquals(0, dto.getFavoriteVenues().size());
    }

    @Test
    void filterByPreferenceRejectsBlankKeyOrValue() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.filterByPreference(" ", "CONCERT"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).findByPreferenceKeyValue(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void filterByPreferenceDelegatesToRepository() {
        User firstUser = new User();
        firstUser.setId(1L);
        User secondUser = new User();
        secondUser.setId(2L);
        List<User> expectedUsers = List.of(firstUser, secondUser);

        when(userRepository.findByPreferenceKeyValue("favoriteCategory", "CONCERT")).thenReturn(expectedUsers);

        List<User> actualUsers = userService.filterByPreference("favoriteCategory", "CONCERT");

        assertIterableEquals(expectedUsers, actualUsers);
    }

    @Test
    void setDefaultVenueUpdatesOnlyTargetAndReturnsLoadedUser() {
        User user = new User();
        user.setId(7L);

        FavoriteVenue firstVenue = venue(11L, user, false);
        FavoriteVenue targetVenue = venue(12L, user, true);
        FavoriteVenue thirdVenue = venue(13L, user, false);
        List<FavoriteVenue> favoriteVenues = List.of(firstVenue, targetVenue, thirdVenue);

        User loadedUser = new User();
        loadedUser.setId(7L);
        loadedUser.setFavoriteVenues(favoriteVenues);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(favoriteVenueRepository.findById(12L)).thenReturn(Optional.of(targetVenue));
        when(favoriteVenueRepository.findByUserIdOrderByIdAsc(7L)).thenReturn(favoriteVenues);
        when(userRepository.findByIdWithFavoriteVenues(7L)).thenReturn(Optional.of(loadedUser));

        User actualUser = userService.setDefaultVenue(7L, 12L);

        verify(favoriteVenueRepository).saveAll(venuesCaptor.capture());
        List<FavoriteVenue> savedVenues = venuesCaptor.getValue();
        assertEquals(3, savedVenues.size());
        assertEquals(Boolean.FALSE, firstVenue.getIsDefault());
        assertEquals(Boolean.TRUE, targetVenue.getIsDefault());
        assertEquals(Boolean.FALSE, thirdVenue.getIsDefault());
        assertEquals(loadedUser, actualUser);
        assertEquals(favoriteVenues, actualUser.getFavoriteVenues());
    }

    @Test
    void setDefaultVenueRejectsVenueOwnedByDifferentUser() {
        User user = new User();
        user.setId(7L);

        User otherUser = new User();
        otherUser.setId(99L);
        FavoriteVenue foreignVenue = venue(12L, otherUser, false);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(favoriteVenueRepository.findById(12L)).thenReturn(Optional.of(foreignVenue));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.setDefaultVenue(7L, 12L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(favoriteVenueRepository, never()).findByUserIdOrderByIdAsc(7L);
        verify(favoriteVenueRepository, never()).saveAll(anyList());
        verify(userRepository, never()).findByIdWithFavoriteVenues(7L);
    }

    private FavoriteVenue venue(Long id, User user, boolean isDefault) {
        FavoriteVenue venue = new FavoriteVenue();
        venue.setId(id);
        venue.setUser(user);
        venue.setIsDefault(isDefault);
        venue.setMetadata(Map.of());
        return venue;
    }
}
