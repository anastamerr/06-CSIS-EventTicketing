package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.booking.dto.AttendanceResponse;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.AttendanceGraphRepository;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAttendanceServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private AttendanceGraphRepository attendanceGraphRepository;

    @Mock
    private BookingService bookingService;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private EventServiceClient eventServiceClient;

    private UserAttendanceService userAttendanceService;

    @BeforeEach
    void setUp() {
        userAttendanceService = new UserAttendanceService(
                bookingRepository,
                attendanceGraphRepository,
                bookingService,
                redisCacheService,
                userServiceClient,
                eventServiceClient);
    }

    @Test
    void recordAttendanceUsesFeignForUserAndEventDetails() {
        Booking booking = new Booking();
        booking.setId(77L);
        booking.setUserId(20L);
        booking.setEventId(10L);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findById(77L)).thenReturn(Optional.of(booking));
        when(userServiceClient.getUser(20L)).thenReturn(new UserDTO(20L, "Nora", "nora@example.com", "ATTENDEE"));
        when(eventServiceClient.getEvent(10L)).thenReturn(new EventDTO(
                10L, "Jazz Night", "Hall", null, "CONCERT", "UPCOMING", 0.0, null));

        AttendanceResponse response = userAttendanceService.recordAttendance(77L);

        assertEquals(77L, response.bookingId());
        verify(attendanceGraphRepository).recordAttendance(20L, "Nora", 10L, "Jazz Night", "CONCERT", 77L);
        verify(redisCacheService).deleteByPattern("booking-service::S3-F12::*");
    }
}
