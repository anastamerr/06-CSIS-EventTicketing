package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.booking.dto.AttendanceResponse;
import com.team06.eventticketing.booking.dto.BookingAttendanceData;
import com.team06.eventticketing.booking.repository.AttendanceGraphRepository;
import com.team06.eventticketing.booking.repository.BookingAttendanceLookupRepository;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.common.observer.EntityObserver;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserAttendanceServiceTest {

    @Mock
    private BookingAttendanceLookupRepository lookupRepository;

    @Mock
    private AttendanceGraphRepository attendanceGraphRepository;

    @Mock
    private BookingService bookingService;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private EntityObserver observer;

    private UserAttendanceService userAttendanceService;

    @BeforeEach
    void setUp() {
        userAttendanceService = new UserAttendanceService(
                lookupRepository,
                attendanceGraphRepository,
                bookingService,
                redisCacheService);
    }

    @Test
    void recordAttendanceCreatesGraphEdgeLogsInteractionAndInvalidatesRecommendations() {
        BookingAttendanceData data = attendanceData("COMPLETED", 10L);
        when(lookupRepository.findAttendanceDataByBookingId(77L)).thenReturn(Optional.of(data));
        when(attendanceGraphRepository.alreadyRecorded(20L, 10L, 77L)).thenReturn(false);

        AttendanceResponse response = userAttendanceService.recordAttendance(77L);

        assertEquals(77L, response.bookingId());
        assertEquals(20L, response.userId());
        assertEquals(10L, response.eventId());
        assertEquals(false, response.alreadyRecorded());
        verify(attendanceGraphRepository).recordAttendance(20L, "Nora", 10L, "Jazz Night", "CONCERT", 77L);
        verify(bookingService).notifyObservers("INTERACTION_RECORDED", java.util.Map.of(
                "bookingId", 77L,
                "userId", 20L,
                "eventId", 10L));
        verify(redisCacheService).deleteByPattern("booking-service::S3-F12::*");
    }

    @Test
    void recordAttendanceIsIdempotentForPreviouslyRecordedBooking() {
        BookingAttendanceData data = attendanceData("COMPLETED", 10L);
        when(lookupRepository.findAttendanceDataByBookingId(77L)).thenReturn(Optional.of(data));
        when(attendanceGraphRepository.alreadyRecorded(20L, 10L, 77L)).thenReturn(true);

        AttendanceResponse response = userAttendanceService.recordAttendance(77L);

        assertTrue(response.alreadyRecorded());
        verify(attendanceGraphRepository, never()).recordAttendance(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(bookingService, never()).notifyObservers(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(redisCacheService, never()).deleteByPattern(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordAttendanceRejectsNonCompletedBookingsAndMissingEventOrBooking() {
        when(lookupRepository.findAttendanceDataByBookingId(1L))
                .thenReturn(Optional.of(attendanceData("CONFIRMED", 10L)));
        when(lookupRepository.findAttendanceDataByBookingId(2L))
                .thenReturn(Optional.of(attendanceData("COMPLETED", null)));
        when(lookupRepository.findAttendanceDataByBookingId(3L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> userAttendanceService.recordAttendance(1L)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> userAttendanceService.recordAttendance(2L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> userAttendanceService.recordAttendance(3L)).getStatusCode());
    }

    private BookingAttendanceData attendanceData(String status, Long eventId) {
        return new BookingAttendanceData(77L, status, 20L, "Nora", eventId, "Jazz Night", "CONCERT");
    }
}
