package com.team06.eventticketing.ticket.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.ticket.service.TicketService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

@ExtendWith(MockitoExtension.class)
class BookingSagaConsumerTest {

    @Mock
    private TicketService ticketService;

    private BookingSagaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BookingSagaConsumer(ticketService, new ObjectMapper());
    }

    @Test
    void bookingPlacedMapPayloadBackfillsEventId() {
        consumer.consumeBookingEvent(
                Map.of("bookingId", 55L, "userId", 9L, "eventId", 77L),
                TicketEventConfig.BOOKING_PLACED_ROUTING_KEY,
                "corr-1");

        verify(ticketService).captureEventIdForBooking(55L, 77L);
    }

    @Test
    void bookingCompletedTypedPayloadPublishesAuditSignals() {
        consumer.consumeBookingEvent(
                new BookingCompletedEvent(55L, 9L, 77L, BigDecimal.valueOf(100)),
                TicketEventConfig.BOOKING_COMPLETED_ROUTING_KEY,
                "corr-2");

        verify(ticketService).publishStatusChangedAuditSignals(55L);
    }

    @Test
    void bookingCancelledTypedPayloadCancelsTickets() {
        consumer.consumeBookingEvent(
                new BookingCancelledEvent(55L, 9L, 77L, "payment_failed"),
                TicketEventConfig.BOOKING_CANCELLED_ROUTING_KEY,
                "corr-3");

        verify(ticketService).cancelTicketsForBooking(55L);
    }

    @Test
    void unsupportedRoutingKeyIsRejectedToDlq() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                consumer.consumeBookingEvent(
                        new BookingPlacedEvent(55L, 9L, 77L),
                        "booking.unknown",
                        "corr-4"));
    }
}
