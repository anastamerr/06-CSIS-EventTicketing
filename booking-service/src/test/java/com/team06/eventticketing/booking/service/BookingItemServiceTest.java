package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.booking.dto.BookingItemRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.repository.BookingItemRepository;
import com.team06.eventticketing.booking.repository.BookingRepository;
import java.util.List;
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
class BookingItemServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingItemRepository bookingItemRepository;

    @Captor
    private ArgumentCaptor<BookingItem> itemCaptor;

    private BookingItemService bookingItemService;

    @BeforeEach
    void setUp() {
        bookingItemService = new BookingItemService(bookingRepository, bookingItemRepository);
    }

    @Test
    void createBookingItemAttachesToBookingAndDefaultsStatus() {
        Booking booking = new Booking();
        booking.setId(9L);
        when(bookingRepository.findById(9L)).thenReturn(Optional.of(booking));

        BookingItemRequest request = new BookingItemRequest();
        request.setEventOrder(1);
        request.setSessionId(100L);
        request.setSessionTitle("Opening");
        request.setQuantity(2);
        request.setUnitPrice(150.0);

        BookingItem saved = new BookingItem();
        saved.setId(1L);
        saved.setStatus(BookingItemStatus.RESERVED);
        when(bookingItemRepository.save(org.mockito.ArgumentMatchers.any(BookingItem.class))).thenReturn(saved);

        BookingItem result = bookingItemService.createBookingItem(9L, request);

        verify(bookingItemRepository).save(itemCaptor.capture());
        BookingItem captured = itemCaptor.getValue();
        assertEquals(booking, captured.getBooking());
        assertEquals(BookingItemStatus.RESERVED, captured.getStatus());
        assertEquals(BookingItemStatus.RESERVED, result.getStatus());
    }

    @Test
    void deleteBookingItemRejectsForeignOrMissingItem() {
        Booking booking = new Booking();
        booking.setId(9L);
        when(bookingRepository.findById(9L)).thenReturn(Optional.of(booking));
        when(bookingItemRepository.findByIdAndBookingId(3L, 9L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingItemService.deleteBookingItem(9L, 3L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getBookingItemsUsesOrderByQuery() {
        Booking booking = new Booking();
        booking.setId(9L);
        when(bookingRepository.findById(9L)).thenReturn(Optional.of(booking));
        when(bookingItemRepository.findByBookingIdOrderByEventOrderAsc(9L)).thenReturn(List.of());

        bookingItemService.getBookingItems(9L);

        verify(bookingItemRepository).findByBookingIdOrderByEventOrderAsc(9L);
    }
}
