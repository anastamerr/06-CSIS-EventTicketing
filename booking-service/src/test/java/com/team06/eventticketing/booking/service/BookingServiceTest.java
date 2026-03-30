package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.booking.dto.BookingRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.booking.repository.TicketSaleJdbcRepository;
import java.util.LinkedHashMap;
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
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketSaleJdbcRepository ticketSaleJdbcRepository;

    @Captor
    private ArgumentCaptor<Map<String, Object>> transactionDetailsCaptor;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, ticketSaleJdbcRepository);
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
    void completeBookingCalculatesTotalCreatesSaleAndCompletesBooking() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setUserId(11L);
        booking.setEventId(22L);
        booking.setContactEmail("buyer@example.com");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setMetadata(new LinkedHashMap<>(Map.of("paymentMethod", "credit_card")));
        booking.addBookingItem(bookingItem(1, 2, 100.0, BookingItemStatus.RESERVED));
        booking.addBookingItem(bookingItem(2, 1, 150.0, BookingItemStatus.CONFIRMED));
        booking.addBookingItem(bookingItem(3, 5, 999.0, BookingItemStatus.REFUNDED));

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));
        when(ticketSaleJdbcRepository.existsByBookingId(5L)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(5L);

        assertEquals(BookingStatus.COMPLETED, result.getStatus());
        assertEquals(350.0, result.getTotalAmount());
        assertEquals(BookingItemStatus.CONFIRMED, result.getBookingItems().get(0).getStatus());
        assertEquals(BookingItemStatus.CONFIRMED, result.getBookingItems().get(1).getStatus());
        assertEquals(BookingItemStatus.REFUNDED, result.getBookingItems().get(2).getStatus());
        assertTrue(Boolean.TRUE.equals(result.getMetadata().get("completed")));

        verify(ticketSaleJdbcRepository).createCompletedSale(
                eq(5L),
                eq(11L),
                eq(350.0),
                eq("CREDIT_CARD"),
                transactionDetailsCaptor.capture()
        );

        Map<String, Object> transactionDetails = transactionDetailsCaptor.getValue();
        assertEquals("booking-service.complete", transactionDetails.get("source"));
        assertEquals(22L, transactionDetails.get("eventId"));
        verify(bookingRepository).save(booking);
    }

    @Test
    void completeBookingRejectsPendingBooking() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(5L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleJdbcRepository, never()).existsByBookingId(any());
        verify(ticketSaleJdbcRepository, never()).createCompletedSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void completeBookingRejectsDuplicateTicketSale() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setUserId(11L);
        booking.setEventId(22L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.addBookingItem(bookingItem(1, 1, 100.0, BookingItemStatus.RESERVED));

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));
        when(ticketSaleJdbcRepository.existsByBookingId(5L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(5L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleJdbcRepository, never()).createCompletedSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getAllBookingsUsesFetchJoinRepository() {
        when(bookingRepository.findAllWithBookingItems()).thenReturn(List.of());

        bookingService.getAllBookings();

        verify(bookingRepository).findAllWithBookingItems();
    }

    private BookingItem bookingItem(int eventOrder, int quantity, double unitPrice, BookingItemStatus status) {
        BookingItem bookingItem = new BookingItem();
        bookingItem.setEventOrder(eventOrder);
        bookingItem.setSessionId((long) eventOrder);
        bookingItem.setSessionTitle("Session " + eventOrder);
        bookingItem.setQuantity(quantity);
        bookingItem.setUnitPrice(unitPrice);
        bookingItem.setStatus(status);
        bookingItem.setMetadata(new LinkedHashMap<>());
        return bookingItem;
    }
}
