package com.team06.eventticketing.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.user.dto.TopAttendeeDTO;
import com.team06.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.user.dto.UserProfileDTO;
import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
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

        FavoriteVenue firstVenue = venue(11L, user, true);
        firstVenue.setLabel("Go-To");
        firstVenue.setVenueName("Cairo Opera");
        firstVenue.setLocation("Zamalek");
        FavoriteVenue secondVenue = venue(12L, user, false);
        secondVenue.setLabel("Weekend");
        secondVenue.setVenueName("Cairo Stadium");
        secondVenue.setLocation("Nasr City");
        FavoriteVenue thirdVenue = venue(13L, user, false);
        thirdVenue.setLabel("Home");
        thirdVenue.setVenueName("CFC Arena");
        thirdVenue.setLocation("New Cairo");

        user.setFavoriteVenues(new ArrayList<>(List.of(firstVenue, secondVenue, thirdVenue)));

        when(userRepository.findByIdWithFavoriteVenues(1L)).thenReturn(Optional.of(user));

        UserProfileDTO dto = userService.getUserProfile(1L);

        assertEquals(1L, dto.getUserId());
        assertEquals("Ahmed", dto.getName());
        assertEquals("ahmed@mail.com", dto.getEmail());
        assertEquals(3, dto.getTotalFavoriteVenues());
        assertEquals(3, dto.getFavoriteVenues().size());
        assertEquals("Cairo Opera", dto.getFavoriteVenues().getFirst().getVenueName());
        assertEquals(Boolean.TRUE, dto.getFavoriteVenues().getFirst().getIsDefault());
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
    void searchUsersNormalizesBlankFiltersBeforeDelegating() {
        List<User> expectedUsers = List.of(new User());
        when(userRepository.searchByOptionalNameEmailRole(isNull(), isNull(), isNull())).thenReturn(expectedUsers);

        List<User> actualUsers = userService.searchUsers(" ", "\t", "");

        assertIterableEquals(expectedUsers, actualUsers);
    }

    @Test
    void searchUsersDelegatesToRepositoryWithProvidedFilters() {
        User firstMatch = new User();
        firstMatch.setId(1L);
        User secondMatch = new User();
        secondMatch.setId(2L);
        List<User> expectedUsers = List.of(firstMatch, secondMatch);

        when(userRepository.searchByOptionalNameEmailRole("Ahmed", null, "ADMIN")).thenReturn(expectedUsers);

        List<User> actualUsers = userService.searchUsers("Ahmed", null, "ADMIN");

        assertIterableEquals(expectedUsers, actualUsers);
    }

    @Test
    void getTopAttendeesBySpendingRejectsInvalidDateRange() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getTopAttendeesBySpending(LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 3, 1), 5));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).findTopAttendeesBySpending(any(), any(), any());
    }

    @Test
    void getTopAttendeesBySpendingRejectsNonPositiveLimit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getTopAttendeesBySpending(LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31), 0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).findTopAttendeesBySpending(any(), any(), any());
    }

    @Test
    void getTopAttendeesBySpendingMapsNativeRowsIntoDtoValues() {
        when(userRepository.findTopAttendeesBySpending(any(), any(), any())).thenReturn(List.of(
                new Object[]{BigInteger.valueOf(7L), "Top User", BigDecimal.valueOf(5000), BigInteger.valueOf(3L)},
                new Object[]{BigInteger.valueOf(8L), "Fallback User", null, null}
        ));

        List<TopAttendeeDTO> result = userService.getTopAttendeesBySpending(LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), 2);

        assertEquals(2, result.size());
        assertEquals(7L, result.getFirst().getUserId());
        assertEquals("Top User", result.getFirst().getName());
        assertEquals(BigDecimal.valueOf(5000), result.getFirst().getTotalSpent());
        assertEquals(3L, result.getFirst().getBookingCount());
        assertEquals(8L, result.get(1).getUserId());
        assertEquals(BigDecimal.ZERO, result.get(1).getTotalSpent());
        assertEquals(0L, result.get(1).getBookingCount());
    }

    @Test
    void getUserBookingSummaryRejectsMissingUser() {
        when(userRepository.findById(33L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getUserBookingSummary(33L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(userRepository, never()).findBookingSummaryByUserId(33L);
    }

    @Test
    void getUserBookingSummaryMapsBigIntegerAndBigDecimalValues() {
        User user = new User();
        user.setId(7L);
        user.setName("Nora");

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.findBookingSummaryByUserId(7L)).thenReturn(List.<Object[]>of(new Object[]{
                BigInteger.valueOf(7L),
                "Nora",
                BigInteger.valueOf(5L),
                BigInteger.valueOf(3L),
                BigInteger.valueOf(1L),
                new BigDecimal("1500.50"),
                new BigDecimal("500.1667")
        }));

        UserBookingSummaryDTO summary = userService.getUserBookingSummary(7L);

        assertEquals(7L, summary.getUserId());
        assertEquals("Nora", summary.getName());
        assertEquals(5L, summary.getTotalBookings());
        assertEquals(3L, summary.getCompletedBookings());
        assertEquals(1L, summary.getCancelledBookings());
        assertEquals(new BigDecimal("1500.50"), summary.getTotalSpent());
        assertEquals(new BigDecimal("500.1667"), summary.getAverageBookingAmount());
    }

    @Test
    void getUserBookingSummaryFallsBackToZeroValues() {
        User user = new User();
        user.setId(9L);
        user.setName("Sara");

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.findBookingSummaryByUserId(9L)).thenReturn(List.<Object[]>of(new Object[]{
                null,
                null,
                null,
                null,
                null,
                null,
                null
        }));

        UserBookingSummaryDTO summary = userService.getUserBookingSummary(9L);

        assertEquals(9L, summary.getUserId());
        assertEquals("Sara", summary.getName());
        assertEquals(0L, summary.getTotalBookings());
        assertEquals(0L, summary.getCompletedBookings());
        assertEquals(0L, summary.getCancelledBookings());
        assertEquals(BigDecimal.ZERO, summary.getTotalSpent());
        assertEquals(BigDecimal.ZERO, summary.getAverageBookingAmount());
    }

    @Test
    void getUserBookingSummaryUnwrapsNestedNativeRows() {
        User user = new User();
        user.setId(11L);
        user.setName("Rana");

        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(userRepository.findBookingSummaryByUserId(11L)).thenReturn(List.<Object[]>of(new Object[]{
                new Object[]{
                        BigInteger.valueOf(11L),
                        "Rana",
                        BigInteger.valueOf(4L),
                        BigInteger.valueOf(2L),
                        BigInteger.valueOf(1L),
                        new BigDecimal("900.00"),
                        new BigDecimal("450.00")
                }
        }));

        UserBookingSummaryDTO summary = userService.getUserBookingSummary(11L);

        assertEquals(11L, summary.getUserId());
        assertEquals("Rana", summary.getName());
        assertEquals(4L, summary.getTotalBookings());
        assertEquals(2L, summary.getCompletedBookings());
        assertEquals(1L, summary.getCancelledBookings());
        assertEquals(new BigDecimal("900.00"), summary.getTotalSpent());
        assertEquals(new BigDecimal("450.00"), summary.getAverageBookingAmount());
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
        venue.setLabel("Label " + id);
        venue.setVenueName("Venue " + id);
        venue.setLocation("Location " + id);
        venue.setMetadata(Map.of());
        return venue;
    }
}
