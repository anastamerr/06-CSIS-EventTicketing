package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.team06.eventticketing.booking.repository.TicketJdbcRepository;
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

    @Mock
    private TicketJdbcRepository ticketJdbcRepository;

    @Captor
    private ArgumentCaptor<Map<String, Object>> transactionDetailsCaptor;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, ticketJdbcRepository, ticketSaleJdbcRepository);
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
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setMetadata(new LinkedHashMap<>(Map.of("paymentMethod", "credit_card")));
        booking.addBookingItem(bookingItem(1, 2, 100.0, BookingItemStatus.RESERVED));
        booking.addBookingItem(bookingItem(2, 1, 250.0, BookingItemStatus.RESERVED));
        booking.addBookingItem(bookingItem(3, 4, 50.0, BookingItemStatus.RESERVED));

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));
        when(ticketSaleJdbcRepository.existsByBookingId(5L)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(5L);

        assertEquals(BookingStatus.COMPLETED, result.getStatus());
        assertEquals(650.0, result.getTotalAmount());

        verify(ticketSaleJdbcRepository).createPendingSale(
                eq(5L),
                eq(11L),
                eq(650.0),
                eq("CREDIT_CARD"),
                transactionDetailsCaptor.capture()
        );

        Map<String, Object> transactionDetails = transactionDetailsCaptor.getValue();
        assertEquals(650.0, transactionDetails.get("bookingTotalAmount"));
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
        verify(ticketSaleJdbcRepository, never()).createPendingSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void completeBookingRejectsDuplicateTicketSale() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setUserId(11L);
        booking.setEventId(22L);
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.addBookingItem(bookingItem(1, 1, 100.0, BookingItemStatus.RESERVED));

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));
        when(ticketSaleJdbcRepository.existsByBookingId(5L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(5L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleJdbcRepository, never()).createPendingSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void completeBookingRejectsAlreadyCompletedBooking() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(5L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleJdbcRepository, never()).existsByBookingId(any());
        verify(ticketSaleJdbcRepository, never()).createPendingSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getAllBookingsUsesFetchJoinRepository() {
        when(bookingRepository.findAllWithBookingItems()).thenReturn(List.of());

        bookingService.getAllBookings();

        verify(bookingRepository).findAllWithBookingItems();
    }

    @Test
    void cancelBookingCancelsConfirmedBookingAndValidTickets() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.cancelBooking(9L);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(ticketJdbcRepository).cancelValidTicketsForBooking(9L);
        verify(bookingRepository).save(booking);
    }

    @Test
    void cancelBookingRejectsCompletedBooking() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancelBooking(9L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketJdbcRepository, never()).cancelValidTicketsForBooking(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBookingRejectsCheckedInBooking() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.CHECKED_IN);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancelBooking(9L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketJdbcRepository, never()).cancelValidTicketsForBooking(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBookingRejectsMissingBooking() {
        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancelBooking(9L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(ticketJdbcRepository, never()).cancelValidTicketsForBooking(any());
        verify(bookingRepository, never()).save(any());
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
