package com.team06.eventticketing.booking.messaging;

import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.messaging.EventTicketingMessagingContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishBookingPlaced(BookingPlacedEvent event) {
        publish(
                EventTicketingMessagingContracts.BOOKING_PLACED_ROUTING_KEY,
                event,
                event.bookingId(),
                event.userId(),
                event.eventId());
    }

    public void publishBookingCompleted(BookingCompletedEvent event) {
        publish(
                EventTicketingMessagingContracts.BOOKING_COMPLETED_ROUTING_KEY,
                event,
                event.bookingId(),
                event.userId(),
                event.eventId());
    }

    public void publishBookingCancelled(BookingCancelledEvent event) {
        publish(
                EventTicketingMessagingContracts.BOOKING_CANCELLED_ROUTING_KEY,
                event,
                event.bookingId(),
                event.userId(),
                event.eventId());
    }

    private void publish(String routingKey, Object event, Long bookingId, Long userId, Long eventId) {
        putIfPresent("routingKey", routingKey);
        putIfPresent("bookingId", bookingId);
        putIfPresent("userId", userId);
        putIfPresent("eventId", eventId);
        String correlationId = MDC.get("correlationId");
        try {
            rabbitTemplate.convertAndSend(
                    BookingEventConfig.BOOKING_EXCHANGE,
                    routingKey,
                    event,
                    message -> {
                        if (correlationId != null && !correlationId.isBlank()) {
                            message.getMessageProperties().setHeader("correlationId", correlationId);
                        }
                        return message;
                    });
            log.info("Published {}", routingKey);
        } finally {
            MDC.remove("eventId");
            MDC.remove("userId");
            MDC.remove("bookingId");
            MDC.remove("routingKey");
        }
    }

    private void putIfPresent(String key, Object value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }
}
