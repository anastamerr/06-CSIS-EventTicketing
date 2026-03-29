package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.booking.dto.BookingRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository);
    }

    @Test
    void createBookingDefaultsStatusWhenMissing() {
        BookingRequest request = new BookingRequest();
        request.setUserId(1L);
        request.setContactEmail("buyer@example.com");

        Booking persisted = new Booking();
        persisted.setId(1L);
        persisted.setUserId(1L);
        persisted.setContactEmail("buyer@example.com");
        persisted.setStatus(BookingStatus.PENDING);

        when(bookingRepository.save(org.mockito.ArgumentMatchers.any(Booking.class))).thenReturn(persisted);

        Booking result = bookingService.createBooking(request);

        assertEquals(BookingStatus.PENDING, result.getStatus());
        verify(bookingRepository).save(org.mockito.ArgumentMatchers.any(Booking.class));
    }

    @Test
    void updateBookingRejectsMissingBooking() {
        when(bookingRepository.findByIdWithBookingItems(7L)).thenReturn(Optional.empty());

        BookingRequest request = new BookingRequest();
        request.setContactEmail("updated@example.com");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.updateBooking(7L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getAllBookingsUsesFetchJoinRepository() {
        when(bookingRepository.findAllWithBookingItems()).thenReturn(List.of());

        bookingService.getAllBookings();

        verify(bookingRepository).findAllWithBookingItems();
    }
}
