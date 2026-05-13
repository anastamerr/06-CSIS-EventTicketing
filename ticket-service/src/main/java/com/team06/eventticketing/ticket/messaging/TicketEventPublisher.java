package com.team06.eventticketing.ticket.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TicketEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTicketIssued(TicketIssuedEvent event) {
        rabbitTemplate.convertAndSend(TicketEventConfig.TICKET_EXCHANGE, "ticket.issued", event);
    }

    public record TicketIssuedEvent(Long ticketId, Long bookingId, Long eventId, String ticketCode) {}
}