package com.team06.eventticketing.event.messaging;

import java.util.Map;
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

    @RabbitListener(queues = EventEventConfig.EVENT_BOOKING_QUEUE)
    public void consumeBookingEvent(
            @Payload Object event,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(name = "correlationId", required = false) String correlationId
    ) {
        logBookingEvent(extractEventId(event), routingKey, correlationId, "Consumed " + routingKey + " for event-service read model");
    }

    private void logBookingEvent(Long eventId, String routingKey, String correlationId, String message) {
        if (eventId != null) {
            MDC.put("eventId", eventId.toString());
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
            MDC.remove("eventId");
        }
    }

    private Long extractEventId(Object event) {
        if (event instanceof Map<?, ?> map) {
            Object value = map.get("eventId");
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                return Long.valueOf(value.toString());
            }
        }
        return null;
    }
}
