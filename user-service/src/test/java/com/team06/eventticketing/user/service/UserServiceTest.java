package com.team06.eventticketing.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;

import com.team06.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team06.eventticketing.contracts.feign.BookingServiceClient;
import com.team06.eventticketing.user.dto.TopAttendeeDTO;
import com.team06.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.user.dto.UserProfileDTO;
import com.team06.eventticketing.user.messaging.UserEventPublisher;
import com.team06.eventticketing.user.model.FavoriteVenue;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.model.UserRole;
import com.team06.eventticketing.user.model.UserStatus;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private BookingServiceClient bookingServiceClient;

    @Mock
    private UserEventPublisher userEventPublisher;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, favoriteVenueRepository, bookingServiceClient, userEventPublisher);
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
    void deactivateUserRejectsActiveBookings() {
        User user = new User();
        user.setId(4L);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getUserActiveBookingCount(4L)).thenReturn(1);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.deactivateUser(4L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deactivateUserRejectsAlreadyDeactivatedUser() {
        User user = new User();
        user.setId(6L);
        user.setStatus(UserStatus.DEACTIVATED);

        when(userRepository.findById(6L)).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.deactivateUser(6L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingServiceClient, never()).getUserActiveBookingCount(6L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deactivateUserPersistsDeactivatedStatusWhenNoActiveBookingsExist() {
        User user = new User();
        user.setId(5L);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getUserActiveBookingCount(5L)).thenReturn(0);
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.deactivateUser(5L);

        assertEquals(UserStatus.DEACTIVATED, result.getStatus());
        verify(userRepository).save(user);
        verify(userEventPublisher).publishUserDeactivated(org.mockito.ArgumentMatchers.argThat(
                event -> event.userId().equals(5L)));
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
    void getUsersByFavoriteCategoryRejectsBlankCategory() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getUsersByFavoriteCategoryAndMinBookings(" ", 1));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).findByFavoriteCategory(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getUsersByFavoriteCategoryRejectsNegativeMinBookings() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getUsersByFavoriteCategoryAndMinBookings("CONCERT", -1));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).findByFavoriteCategory(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getUsersByFavoriteCategoryFiltersCandidatesWithFeignCompletedCount() {
        User firstUser = new User();
        firstUser.setId(1L);
        User secondUser = new User();
        secondUser.setId(2L);

        when(userRepository.findByFavoriteCategory("CONCERT")).thenReturn(List.of(firstUser, secondUser));
        when(bookingServiceClient.getUserBookingCount(1L, "COMPLETED")).thenReturn(5L);
        when(bookingServiceClient.getUserBookingCount(2L, "COMPLETED")).thenReturn(2L);

        List<User> actualUsers = userService.getUsersByFavoriteCategoryAndMinBookings("CONCERT", 3);

        assertIterableEquals(List.of(firstUser), actualUsers);
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
        verify(userRepository, never()).findAll();
    }

    @Test
    void getTopAttendeesBySpendingRejectsNonPositiveLimit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getTopAttendeesBySpending(LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31), 0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).findAll();
    }

    @Test
    void getTopAttendeesBySpendingUsesFeignTotalsAndSortsDescending() {
        User first = new User();
        first.setId(7L);
        first.setName("Second User");
        first.setRole(UserRole.ATTENDEE);
        User second = new User();
        second.setId(8L);
        second.setName("Top User");
        second.setRole(UserRole.ATTENDEE);
        User admin = new User();
        admin.setId(9L);
        admin.setRole(UserRole.ADMIN);

        when(userRepository.findAll()).thenReturn(List.of(first, second, admin));
        when(bookingServiceClient.getUserCompletedBookingTotal(7L, "2026-03-01", "2026-03-31"))
                .thenReturn(BigDecimal.valueOf(2000));
        when(bookingServiceClient.getUserCompletedBookingTotal(8L, "2026-03-01", "2026-03-31"))
                .thenReturn(BigDecimal.valueOf(5000));
        when(bookingServiceClient.getUserBookingCount(7L, "COMPLETED")).thenReturn(3L);
        when(bookingServiceClient.getUserBookingCount(8L, "COMPLETED")).thenReturn(5L);

        List<TopAttendeeDTO> result = userService.getTopAttendeesBySpending(LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), 2);

        assertEquals(2, result.size());
        assertEquals(8L, result.getFirst().getUserId());
        assertEquals("Top User", result.getFirst().getName());
        assertEquals(BigDecimal.valueOf(5000), result.getFirst().getTotalSpent());
        assertEquals(5L, result.getFirst().getBookingCount());
        assertEquals(7L, result.get(1).getUserId());
        assertEquals(BigDecimal.valueOf(2000), result.get(1).getTotalSpent());
        assertEquals(3L, result.get(1).getBookingCount());
        verify(bookingServiceClient, never()).getUserCompletedBookingTotal(eq(9L), any(), any());
    }

    @Test
    void getUserBookingSummaryRejectsMissingUser() {
        when(userRepository.findById(33L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getUserBookingSummary(33L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(bookingServiceClient, never()).getUserBookingSummary(33L);
    }

    @Test
    void getUserBookingSummaryCombinesLocalUserWithFeignSummary() {
        User user = new User();
        user.setId(7L);
        user.setName("Nora");

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getUserBookingSummary(7L)).thenReturn(new BookingSummaryDTO(
                5L,
                3L,
                1L,
                new BigDecimal("1500.50"),
                new BigDecimal("500.1667")));

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
    void getUserBookingSummaryFallsBackToZeroValuesWhenFeignReturnsNull() {
        User user = new User();
        user.setId(9L);
        user.setName("Sara");

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getUserBookingSummary(9L)).thenReturn(null);

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
    void setDefaultVenueClearsExistingDefaultsAndReturnsLoadedUser() {
        User user = new User();
        user.setId(7L);

        FavoriteVenue firstVenue = venue(11L, user, true);
        FavoriteVenue targetVenue = venue(12L, user, false);
        FavoriteVenue thirdVenue = venue(13L, user, false);
        List<FavoriteVenue> favoriteVenues = List.of(firstVenue, targetVenue, thirdVenue);

        User loadedUser = new User();
        loadedUser.setId(7L);
        loadedUser.setFavoriteVenues(favoriteVenues);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(favoriteVenueRepository.findById(12L)).thenReturn(Optional.of(targetVenue));
        when(favoriteVenueRepository.save(targetVenue)).thenReturn(targetVenue);
        when(userRepository.findByIdWithFavoriteVenues(7L)).thenReturn(Optional.of(loadedUser));

        User actualUser = userService.setDefaultVenue(7L, 12L);

        verify(favoriteVenueRepository).clearDefaultsForUser(7L);
        verify(favoriteVenueRepository).save(targetVenue);
        verify(favoriteVenueRepository, never()).findByUserIdOrderByIdAsc(anyLong());
        assertEquals(Boolean.TRUE, firstVenue.getIsDefault());
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
        verify(favoriteVenueRepository, never()).clearDefaultsForUser(7L);
        verify(favoriteVenueRepository, never()).save(any(FavoriteVenue.class));
        verify(userRepository, never()).findByIdWithFavoriteVenues(7L);
    }

    @Test
    void getUserBookingSummary_whenFeignThrowsGenericException_shouldPropagate503() {
        User user = new User();
        user.setId(1L);
        user.setName("Test User");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getUserBookingSummary(1L)).thenThrow(mock(FeignException.class));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getUserBookingSummary(1L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void deactivateUser_whenFeignThrowsOnActiveCount_shouldPropagate503() {
        User user = new User();
        user.setId(1L);
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getUserActiveBookingCount(1L)).thenThrow(mock(FeignException.class));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.deactivateUser(1L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getTopAttendeesBySpending_whenFeignCallFails_shouldPropagate503() {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        Long userIdOne = 1L;
        Long userIdTwo = 2L;
        BigDecimal spendingUserOne = BigDecimal.valueOf(1000);
        User userOne = new User(); userOne.setId(userIdOne); userOne.setName("User One"); userOne.setRole(UserRole.ATTENDEE);
        User userTwo = new User(); userTwo.setId(userIdTwo); userTwo.setName("User Two"); userTwo.setRole(UserRole.ATTENDEE);
        when(userRepository.findAll()).thenReturn(List.of(userOne, userTwo));
        when(bookingServiceClient.getUserCompletedBookingTotal(userIdOne, "2026-03-01", "2026-03-31")).thenReturn(spendingUserOne);
        when(bookingServiceClient.getUserCompletedBookingTotal(userIdTwo, "2026-03-01", "2026-03-31")).thenThrow(mock(FeignException.class));
        when(bookingServiceClient.getUserBookingCount(userIdOne, "COMPLETED")).thenReturn(2L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getTopAttendeesBySpending(startDate, endDate, 3));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getTopAttendeesBySpending_whenFirstFeignCallFails_shouldPropagate503() {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        User userOne = new User(); userOne.setId(1L); userOne.setRole(UserRole.ATTENDEE);
        User userTwo = new User(); userTwo.setId(2L); userTwo.setRole(UserRole.ATTENDEE);
        when(userRepository.findAll()).thenReturn(List.of(userOne, userTwo));
        when(bookingServiceClient.getUserCompletedBookingTotal(anyLong(), any(), any())).thenThrow(mock(FeignException.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getTopAttendeesBySpending(startDate, endDate, 2));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getUsersByFavoriteCategoryAndMinBookings_whenFeignThrows_shouldPropagate() {
        Long userId = 10L;
        User user = new User(); user.setId(userId);
        when(userRepository.findByFavoriteCategory("CONCERT")).thenReturn(List.of(user));
        when(bookingServiceClient.getUserBookingCount(userId, "COMPLETED")).thenThrow(mock(FeignException.class));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userService.getUsersByFavoriteCategoryAndMinBookings("CONCERT", 3));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
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
