package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team06.eventticketing.booking.dto.BookingEstimateRequest;
import com.team06.eventticketing.booking.messaging.BookingEventPublisher;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.contracts.dto.AvgCapacityDTO;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
import com.team06.eventticketing.contracts.feign.TicketServiceClient;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import java.math.BigDecimal;
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
    private UserServiceClient userServiceClient;

    @Mock
    private TicketServiceClient ticketServiceClient;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                eventServiceClient,
                userServiceClient,
                ticketServiceClient,
                bookingEventPublisher);
    }

    @Test
    void estimateBookingCostUsesEventServiceAverageCapacity() {
        when(eventServiceClient.getEventAvgCapacity(77L)).thenReturn(new AvgCapacityDTO(500.0));
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

    @Test
    void completeBookingRunsPreChecksAndPublishesBookingCompleted() {
        Booking booking = checkedInBooking();
        addItem(booking, 2, 250.0);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(5L)).thenReturn(new EventDTO(
                5L, "Concert", "Hall", null, "CONCERT", "ONGOING", 0.0, null));
        when(userServiceClient.getUser(7L)).thenReturn(new UserDTO(7L, "Ibrahim", "ibrahim@example.com", "USER", "ACTIVE"));
        when(ticketServiceClient.getActiveTicketCountForBooking(10L)).thenReturn(1);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(10L);

        assertEquals(BookingStatus.COMPLETING, result.getStatus());
        assertEquals(500.0, result.getTotalAmount());
        verify(bookingEventPublisher).publishBookingCompleted(new BookingCompletedEvent(
                10L,
                7L,
                5L,
                BigDecimal.valueOf(500.0)));
    }

    @Test
    void completeBookingRejectsUpcomingEventAndPublishesNoEvent() {
        Booking booking = checkedInBooking();

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(5L)).thenReturn(new EventDTO(
                5L, "Concert", "Hall", null, "CONCERT", "UPCOMING", 0.0, null));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.completeBooking(10L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
        verify(bookingEventPublisher, never()).publishBookingCompleted(any());
    }

    @Test
    void completeBookingRejectsDeactivatedUserAndPublishesNoEvent() {
        Booking booking = checkedInBooking();

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(5L)).thenReturn(new EventDTO(
                5L, "Concert", "Hall", null, "CONCERT", "COMPLETED", 0.0, null));
        when(userServiceClient.getUser(7L)).thenReturn(new UserDTO(7L, "Ibrahim", "ibrahim@example.com", "USER", "DEACTIVATED"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.completeBooking(10L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
        verify(bookingEventPublisher, never()).publishBookingCompleted(any());
    }

    @Test
    void completeBookingRejectsMissingAttendanceAndPublishesNoEvent() {
        Booking booking = checkedInBooking();

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(eventServiceClient.getEvent(5L)).thenReturn(new EventDTO(
                5L, "Concert", "Hall", null, "CONCERT", "ONGOING", 0.0, null));
        when(userServiceClient.getUser(7L)).thenReturn(new UserDTO(7L, "Ibrahim", "ibrahim@example.com", "USER", "ACTIVE"));
        when(ticketServiceClient.getActiveTicketCountForBooking(10L)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.completeBooking(10L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(BookingStatus.CHECKED_IN, booking.getStatus());
        verify(bookingEventPublisher, never()).publishBookingCompleted(any());
    }

    @Test
    void completeBookingRejectsNonCheckedInBooking() {
        Booking booking = checkedInBooking();
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.completeBooking(10L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingEventPublisher, never()).publishBookingCompleted(any());
    }

    @Test
    void cancelBookingPublishesBookingCancelled() {
        Booking booking = checkedInBooking();
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.cancelBooking(10L);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(bookingEventPublisher).publishBookingCancelled(new BookingCancelledEvent(
                10L,
                7L,
                5L,
                "user_requested"));
    }

    @Test
    void cancelBookingRejectsCheckedInBooking() {
        Booking booking = checkedInBooking();

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.cancelBooking(10L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingEventPublisher, never()).publishBookingCancelled(any());
    }

    @Test
    void paymentInitiatedMovesCompletingBookingToPaymentPending() {
        Booking booking = checkedInBooking();
        booking.setStatus(BookingStatus.COMPLETING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.markPaymentInitiated(10L);

        assertEquals(BookingStatus.PAYMENT_PENDING, result.getStatus());
    }

    @Test
    void paymentCompletedMovesPendingBookingToPaid() {
        Booking booking = checkedInBooking();
        booking.setStatus(BookingStatus.PAYMENT_PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.markPaymentCompleted(10L);

        assertEquals(BookingStatus.PAID, result.getStatus());
    }

    @Test
    void paymentFailedPublishesCompensationCancellation() {
        Booking booking = checkedInBooking();
        booking.setStatus(BookingStatus.PAYMENT_PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.markPaymentFailed(10L, "card_declined");

        assertEquals(BookingStatus.PAYMENT_FAILED, result.getStatus());
        verify(bookingEventPublisher).publishBookingCancelled(new BookingCancelledEvent(
                10L,
                7L,
                5L,
                "payment_failed"));
    }

    @Test
    void paymentRefundedMovesFailedBookingToRefunded() {
        Booking booking = checkedInBooking();
        booking.setStatus(BookingStatus.PAYMENT_FAILED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.markPaymentRefunded(10L);

        assertEquals(BookingStatus.REFUNDED, result.getStatus());
    }

    private Booking checkedInBooking() {
        Booking booking = new Booking();
        booking.setId(10L);
        booking.setUserId(7L);
        booking.setEventId(5L);
        booking.setContactEmail("ibrahim@example.com");
        booking.setStatus(BookingStatus.CHECKED_IN);
        return booking;
    }

    private void addItem(Booking booking, int quantity, double unitPrice) {
        BookingItem item = new BookingItem();
        item.setEventOrder(1);
        item.setSessionId(100L);
        item.setSessionTitle("Main Session");
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        booking.addBookingItem(item);
    }
}
