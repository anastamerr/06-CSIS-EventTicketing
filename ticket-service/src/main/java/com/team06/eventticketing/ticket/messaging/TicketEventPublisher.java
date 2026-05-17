package com.team06.eventticketing.ticket.messaging;

import com.team06.eventticketing.contracts.events.TicketCancelledEvent;
import com.team06.eventticketing.contracts.events.TicketStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TicketEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public TicketEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTicketIssued(TicketIssuedEvent event) {
        publish(
                TicketEventConfig.TICKET_ISSUED_ROUTING_KEY,
                event,
                event.ticketId(),
                event.bookingId(),
                event.eventId());
    }

    public void publishTicketStatusChanged(TicketStatusChangedEvent event, Long eventId) {
        publish(
                TicketEventConfig.TICKET_STATUS_CHANGED_ROUTING_KEY,
                event,
                event.ticketId(),
                event.bookingId(),
                eventId);
    }

    public void publishTicketCancelled(TicketCancelledEvent event, Long eventId) {
        publish(
                TicketEventConfig.TICKET_CANCELLED_ROUTING_KEY,
                event,
                event.ticketId(),
                event.bookingId(),
                eventId);
    }

    private void publish(String routingKey, Object event, Long ticketId, Long bookingId, Long eventId) {
        putIfPresent("ticketId", ticketId);
        putIfPresent("bookingId", bookingId);
        putIfPresent("eventId", eventId);
        putIfPresent("routingKey", routingKey);
        String correlationId = MDC.get("correlationId");
        try {
            rabbitTemplate.convertAndSend(TicketEventConfig.TICKET_EXCHANGE, routingKey, event, message -> {
                if (correlationId != null && !correlationId.isBlank()) {
                    message.getMessageProperties().setHeader("correlationId", correlationId);
                }
                return message;
            });
            log.info("Published {}", routingKey);
        } finally {
            MDC.remove("routingKey");
            MDC.remove("eventId");
            MDC.remove("bookingId");
            MDC.remove("ticketId");
        }
    }

    private void putIfPresent(String key, Object value) {
        if (value != null) {
            MDC.put(key, value.toString());
        }
    }

    public record TicketIssuedEvent(Long ticketId, Long bookingId, Long eventId, String ticketCode) {}
}
