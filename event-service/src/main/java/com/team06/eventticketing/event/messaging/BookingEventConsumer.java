package com.team06.eventticketing.event.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);
    private static final String BOOKING_PLACED = "booking.placed";
    private static final String BOOKING_COMPLETED = "booking.completed";
    private static final String BOOKING_CANCELLED = "booking.cancelled";

    private final ObjectMapper objectMapper;

    public BookingEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = EventEventConfig.EVENT_BOOKING_QUEUE)
    public void consumeBookingEvent(
            @Payload Object event,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(name = "correlationId", required = false) String correlationId
    ) {
        switch (routingKey) {
            case BOOKING_PLACED -> handleBookingPlaced(toBookingPlaced(event), correlationId);
            case BOOKING_COMPLETED -> handleBookingCompleted(toBookingCompleted(event), correlationId);
            case BOOKING_CANCELLED -> handleBookingCancelled(toBookingCancelled(event), correlationId);
            default -> throw new IllegalArgumentException("Unsupported booking routing key: " + routingKey);
        }
    }

    private void handleBookingPlaced(BookingPlacedEvent event, String correlationId) {
        logBookingEvent(event.eventId(), event.bookingId(), BOOKING_PLACED, correlationId,
                "Consuming booking.placed for event-service read model");
        logBookingEvent(event.eventId(), event.bookingId(), BOOKING_PLACED, correlationId,
                "Processed booking.placed for event-service read model");
    }

    private void handleBookingCompleted(BookingCompletedEvent event, String correlationId) {
        logBookingEvent(event.eventId(), event.bookingId(), BOOKING_COMPLETED, correlationId,
                "Consuming booking.completed for event-service statistics");
        logBookingEvent(event.eventId(), event.bookingId(), BOOKING_COMPLETED, correlationId,
                "Processed booking.completed for event-service statistics");
    }

    private void handleBookingCancelled(BookingCancelledEvent event, String correlationId) {
        logBookingEvent(event.eventId(), event.bookingId(), BOOKING_CANCELLED, correlationId,
                "Consuming booking.cancelled for event-service statistics");
        logBookingEvent(event.eventId(), event.bookingId(), BOOKING_CANCELLED, correlationId,
                "Processed booking.cancelled for event-service statistics");
    }

    private void logBookingEvent(Long eventId, Long bookingId, String routingKey, String correlationId, String message) {
        if (eventId != null) {
            MDC.put("eventId", eventId.toString());
        }
        if (bookingId != null) {
            MDC.put("bookingId", bookingId.toString());
        }
        MDC.put("routingKey", routingKey);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put("correlationId", correlationId);
        }
        try {
            log.info(message);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("routingKey");
            MDC.remove("bookingId");
            MDC.remove("eventId");
        }
    }

    private BookingPlacedEvent toBookingPlaced(Object event) {
        if (event instanceof BookingPlacedEvent typedEvent) {
            return typedEvent;
        }
        return objectMapper.convertValue(event, BookingPlacedEvent.class);
    }

    private BookingCompletedEvent toBookingCompleted(Object event) {
        if (event instanceof BookingCompletedEvent typedEvent) {
            return typedEvent;
        }
        return objectMapper.convertValue(event, BookingCompletedEvent.class);
    }

    private BookingCancelledEvent toBookingCancelled(Object event) {
        if (event instanceof BookingCancelledEvent typedEvent) {
            return typedEvent;
        }
        return objectMapper.convertValue(event, BookingCancelledEvent.class);
    }
}
