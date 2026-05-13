package com.team06.eventticketing.user.messaging;

import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);

    private final ObjectMapper objectMapper;

    public BookingEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = UserEventConfig.USER_BOOKING_SAGA_QUEUE)
    public void onBookingEvent(Message message, @Headers Map<String, Object> headers) throws IOException {
        String routingKey = (String) headers.get(AmqpHeaders.RECEIVED_ROUTING_KEY);
        String correlationId = (String) headers.get("x-correlation-id");
        if ("booking.completed".equals(routingKey)) {
            onBookingCompleted(objectMapper.readValue(message.getBody(), BookingCompletedEvent.class), correlationId);
            return;
        }
        if ("booking.cancelled".equals(routingKey)) {
            onBookingCancelled(objectMapper.readValue(message.getBody(), BookingCancelledEvent.class), correlationId);
            return;
        }
        log.warn("Ignoring unsupported booking event routingKey={}", routingKey);
    }

    private void onBookingCompleted(BookingCompletedEvent event, String correlationId) {
        MDC.put("routingKey", "booking.completed");
        MDC.put("bookingId", String.valueOf(event.bookingId()));
        MDC.put("userId", String.valueOf(event.userId()));
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            log.info("Received booking.completed for bookingId={} userId={} totalAmount={}",
                    event.bookingId(), event.userId(), event.totalAmount());
        } finally {
            MDC.remove("routingKey");
            MDC.remove("bookingId");
            MDC.remove("userId");
            MDC.remove("correlationId");
        }
    }

    private void onBookingCancelled(BookingCancelledEvent event, String correlationId) {
        MDC.put("routingKey", "booking.cancelled");
        MDC.put("bookingId", String.valueOf(event.bookingId()));
        MDC.put("userId", String.valueOf(event.userId()));
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            log.info("Received booking.cancelled for bookingId={} userId={} reason={}",
                    event.bookingId(), event.userId(), event.reason());
        } finally {
            MDC.remove("routingKey");
            MDC.remove("bookingId");
            MDC.remove("userId");
            MDC.remove("correlationId");
        }
    }
}
