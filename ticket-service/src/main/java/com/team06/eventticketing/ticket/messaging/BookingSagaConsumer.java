package com.team06.eventticketing.ticket.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.ticket.service.TicketService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class BookingSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingSagaConsumer.class);

    private final TicketService ticketService;
    private final ObjectMapper objectMapper;

    public BookingSagaConsumer(TicketService ticketService, ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = TicketEventConfig.TICKET_BOOKING_SAGA_QUEUE)
    public void consumeBookingMessage(
            Message message,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(name = "correlationId", required = false) String correlationId
    ) {
        consumeBookingEvent(readPayload(message, routingKey), routingKey, correlationId);
    }

    public void consumeBookingEvent(
            Object event,
            String routingKey,
            String correlationId
    ) {
        try {
            switch (routingKey) {
                case TicketEventConfig.BOOKING_PLACED_ROUTING_KEY ->
                        handleBookingPlaced(toBookingPlaced(event), correlationId);
                case TicketEventConfig.BOOKING_COMPLETED_ROUTING_KEY ->
                        handleBookingCompleted(toBookingCompleted(event), correlationId);
                case TicketEventConfig.BOOKING_CANCELLED_ROUTING_KEY ->
                        handleBookingCancelled(toBookingCancelled(event), correlationId);
                default -> throw new IllegalArgumentException("Unsupported booking routing key: " + routingKey);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to process ticket-service saga event for routing key " + routingKey,
                    exception);
        }
    }

    private Object readPayload(Message message, String routingKey) {
        try {
            return switch (routingKey) {
                case TicketEventConfig.BOOKING_PLACED_ROUTING_KEY ->
                        objectMapper.readValue(message.getBody(), BookingPlacedEvent.class);
                case TicketEventConfig.BOOKING_COMPLETED_ROUTING_KEY ->
                        objectMapper.readValue(message.getBody(), BookingCompletedEvent.class);
                case TicketEventConfig.BOOKING_CANCELLED_ROUTING_KEY ->
                        objectMapper.readValue(message.getBody(), BookingCancelledEvent.class);
                default -> throw new IllegalArgumentException("Unsupported booking routing key: " + routingKey);
            };
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to deserialize booking saga payload for " + routingKey, exception);
        }
    }

    private void handleBookingPlaced(BookingPlacedEvent event, String correlationId) {
        requireField(event.bookingId(), "bookingId");
        requireField(event.eventId(), "eventId");
        logWithContext(event.bookingId(), event.eventId(), null,
                TicketEventConfig.BOOKING_PLACED_ROUTING_KEY, correlationId,
                "Consuming booking.placed to capture denormalized eventId");
        int updated = ticketService.captureEventIdForBooking(event.bookingId(), event.eventId());
        logWithContext(event.bookingId(), event.eventId(), null,
                TicketEventConfig.BOOKING_PLACED_ROUTING_KEY, correlationId,
                "Processed booking.placed and backfilled eventId on " + updated + " ticket(s)");
    }

    private void handleBookingCompleted(BookingCompletedEvent event, String correlationId) {
        requireField(event.bookingId(), "bookingId");
        logWithContext(event.bookingId(), event.eventId(), null,
                TicketEventConfig.BOOKING_COMPLETED_ROUTING_KEY, correlationId,
                "Consuming booking.completed to publish ticket.status-changed audit signals");
        int published = ticketService.publishStatusChangedAuditSignals(event.bookingId());
        logWithContext(event.bookingId(), event.eventId(), null,
                TicketEventConfig.BOOKING_COMPLETED_ROUTING_KEY, correlationId,
                "Processed booking.completed and published ticket.status-changed for " + published + " ticket(s)");
    }

    private void handleBookingCancelled(BookingCancelledEvent event, String correlationId) {
        requireField(event.bookingId(), "bookingId");
        logWithContext(event.bookingId(), event.eventId(), null,
                TicketEventConfig.BOOKING_CANCELLED_ROUTING_KEY, correlationId,
                "Consuming booking.cancelled to cancel ticket records");
        int cancelled = ticketService.cancelTicketsForBooking(
                event.bookingId(),
                isPaymentFailureCompensation(event));
        logWithContext(event.bookingId(), event.eventId(), null,
                TicketEventConfig.BOOKING_CANCELLED_ROUTING_KEY, correlationId,
                "Processed booking.cancelled and cancelled " + cancelled + " ticket(s)");
    }

    private boolean isPaymentFailureCompensation(BookingCancelledEvent event) {
        return event.reason() != null && "payment_failed".equalsIgnoreCase(event.reason());
    }

    private void requireField(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }

    private void logWithContext(
            Long bookingId,
            Long eventId,
            Long ticketId,
            String routingKey,
            String correlationId,
            String message
    ) {
        putIfPresent("bookingId", bookingId);
        putIfPresent("eventId", eventId);
        putIfPresent("ticketId", ticketId);
        putIfPresent("routingKey", routingKey);
        putIfPresent("correlationId", correlationId);
        try {
            log.info(message);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("routingKey");
            MDC.remove("ticketId");
            MDC.remove("eventId");
            MDC.remove("bookingId");
        }
    }

    private void putIfPresent(String key, Object value) {
        if (value != null) {
            MDC.put(key, value.toString());
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
