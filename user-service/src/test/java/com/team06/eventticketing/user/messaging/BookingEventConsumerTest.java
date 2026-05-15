package com.team06.eventticketing.user.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.AmqpHeaders;

@ExtendWith(MockitoExtension.class)
class BookingEventConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BookingEventConsumer consumer;

    @Test
    void handleBookingEvent_withCompletedRoutingKey_shouldInvokeCompletedHandler() throws IOException {
        BookingCompletedEvent completedEvent = new BookingCompletedEvent(1L, 10L, 100L, BigDecimal.valueOf(500));
        when(objectMapper.readValue(any(byte[].class), eq(BookingCompletedEvent.class))).thenReturn(completedEvent);
        byte[] body = "{\"bookingId\":1,\"userId\":10,\"eventId\":100,\"totalAmount\":500}".getBytes();
        Message message = new Message(body);
        Map<String, Object> headers = Map.of(AmqpHeaders.RECEIVED_ROUTING_KEY, "booking.completed");
        consumer.onBookingEvent(message, headers);
        verify(objectMapper).readValue(body, BookingCompletedEvent.class);
    }

    @Test
    void handleBookingEvent_withCancelledRoutingKey_shouldInvokeCancelledHandler() throws IOException {
        BookingCancelledEvent cancelledEvent = new BookingCancelledEvent(2L, 20L, 200L, "no reason");
        when(objectMapper.readValue(any(byte[].class), eq(BookingCancelledEvent.class))).thenReturn(cancelledEvent);
        byte[] body = "{\"bookingId\":2,\"userId\":20,\"eventId\":200,\"reason\":\"no reason\"}".getBytes();
        Message message = new Message(body);
        Map<String, Object> headers = Map.of(AmqpHeaders.RECEIVED_ROUTING_KEY, "booking.cancelled");
        consumer.onBookingEvent(message, headers);
        verify(objectMapper).readValue(body, BookingCancelledEvent.class);
    }

    @Test
    void handleBookingEvent_withUnknownRoutingKey_shouldLogWarningAndNotThrow() throws IOException {
        Message message = new Message("{\"bookingId\":3}".getBytes());
        Map<String, Object> headers = Map.of(AmqpHeaders.RECEIVED_ROUTING_KEY, "booking.unknown");
        assertDoesNotThrow(() -> consumer.onBookingEvent(message, headers));
        verify(objectMapper, never()).readValue(any(byte[].class), any(Class.class));
    }
}
