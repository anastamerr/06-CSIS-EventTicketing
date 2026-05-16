package com.team06.eventticketing.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team06.eventticketing.contracts.feign.BookingServiceClient;
import com.team06.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.user.messaging.UserEventPublisher;
import com.team06.eventticketing.user.model.User;
import com.team06.eventticketing.user.repository.FavoriteVenueRepository;
import com.team06.eventticketing.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(UserServiceMockitoBeanBonusTest.Config.class)
class UserServiceMockitoBeanBonusTest {

    @Configuration
    static class Config {
        @Bean
        UserService userService(
                UserRepository userRepository,
                FavoriteVenueRepository favoriteVenueRepository,
                BookingServiceClient bookingServiceClient,
                UserEventPublisher userEventPublisher
        ) {
            return new UserService(userRepository, favoriteVenueRepository, bookingServiceClient, userEventPublisher);
        }
    }

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FavoriteVenueRepository favoriteVenueRepository;

    @MockitoBean
    private BookingServiceClient bookingServiceClient;

    @MockitoBean
    private UserEventPublisher userEventPublisher;

    @Autowired
    private UserService userService;

    @Test
    void userBusinessLogicUsesMockitoBeanFeignClient() {
        User user = new User();
        user.setId(42L);
        user.setName("Bonus User");

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(bookingServiceClient.getUserBookingSummary(42L)).thenReturn(new BookingSummaryDTO(
                4L, 3L, 1L, new BigDecimal("900.00"), new BigDecimal("300.00")));

        UserBookingSummaryDTO result = userService.getUserBookingSummary(42L);

        assertEquals(42L, result.getUserId());
        assertEquals(4L, result.getTotalBookings());
        assertEquals(new BigDecimal("900.00"), result.getTotalSpent());
    }
}
