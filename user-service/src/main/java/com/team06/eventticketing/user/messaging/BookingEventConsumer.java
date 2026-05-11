package com.team06.eventticketing.user.messaging;

import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);

    @RabbitListener(queues = UserEventConfig.USER_BOOKING_SAGA_QUEUE)
    public void onBookingCompleted(BookingCompletedEvent event,
            @Header(value = "x-correlation-id", required = false) String correlationId) {
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

    @RabbitListener(queues = UserEventConfig.USER_BOOKING_SAGA_QUEUE)
    public void onBookingCancelled(BookingCancelledEvent event,
            @Header(value = "x-correlation-id", required = false) String correlationId) {
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
