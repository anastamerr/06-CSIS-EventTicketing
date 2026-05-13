package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team06.eventticketing.booking.dto.BookingEstimateRequest;
import com.team06.eventticketing.booking.messaging.BookingEventPublisher;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.contracts.dto.AvgCapacityDTO;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
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

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, eventServiceClient, bookingEventPublisher);
    }

    @Test
    void estimateBookingCostUsesEventServiceAverageCapacity() {
        when(eventServiceClient.getEventAverageSessionCapacity(77L)).thenReturn(new AvgCapacityDTO(500.0));
        when(bookingRepository.countActiveBookingsByEventId(77L)).thenReturn(0L);

        BookingCostEstimateDTO result = bookingService.estimateBookingCost(new BookingEstimateRequest(77L, 2, "VIP"));

        assertEquals(250.0, result.ticketCost());
        assertEquals(37.5, result.serviceFee());
        assertEquals(287.5, result.estimatedTotal());
    }

    @Test
    void confirmBookingUsesEventServiceAndPublishesBookingPlaced() {
        Booking booking = new Booking();
        booking.setId(15L);
        booking.setUserId(7L);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(15L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(22L)).thenReturn(new EventDTO(
                22L, "Concert", "Hall", null, "CONCERT", "UPCOMING", 0.0, null));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.confirmBooking(15L, 22L);

        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals(22L, result.getEventId());
        verify(bookingEventPublisher).publishBookingPlaced(new BookingPlacedEvent(15L, 7L, 22L));
    }

    @Test
    void confirmBookingRejectsNonUpcomingEvent() {
        Booking booking = new Booking();
        booking.setId(15L);
        booking.setUserId(7L);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(15L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(22L)).thenReturn(new EventDTO(
                22L, "Concert", "Hall", null, "CONCERT", "CANCELLED", 0.0, null));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.confirmBooking(15L, 22L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
