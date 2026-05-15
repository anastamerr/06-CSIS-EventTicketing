package com.team06.eventticketing.ticket.messaging;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketEventConfig {

    public static final String TICKET_EXCHANGE = "ticket.events";
    public static final String BOOKING_EXCHANGE = "booking.events";

    public static final String TICKET_BOOKING_SAGA_QUEUE = "ticket.booking.saga-listener";
    public static final String TICKET_BOOKING_SAGA_DLQ = "ticket.booking.saga-listener.dlq";
    public static final String TICKET_BOOKING_SAGA_DLX = "ticket.booking.saga-listener.dlx";
    public static final String TICKET_BOOKING_SAGA_DLQ_ROUTING_KEY = "ticket.booking.saga-listener.dead";

    public static final String BOOKING_PLACED_ROUTING_KEY = "booking.placed";
    public static final String BOOKING_COMPLETED_ROUTING_KEY = "booking.completed";
    public static final String BOOKING_CANCELLED_ROUTING_KEY = "booking.cancelled";

    public static final String TICKET_ISSUED_ROUTING_KEY = "ticket.issued";
    public static final String TICKET_STATUS_CHANGED_ROUTING_KEY = "ticket.status-changed";
    public static final String TICKET_CANCELLED_ROUTING_KEY = "ticket.cancelled";

    @Bean
    public TopicExchange ticketExchange() {
        return new TopicExchange(TICKET_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange ticketBookingSagaDeadLetterExchange() {
        return new TopicExchange(TICKET_BOOKING_SAGA_DLX, true, false);
    }

    @Bean
    public Queue ticketBookingSagaQueue() {
        return new Queue(TICKET_BOOKING_SAGA_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", TICKET_BOOKING_SAGA_DLX,
                "x-dead-letter-routing-key", TICKET_BOOKING_SAGA_DLQ_ROUTING_KEY));
    }

    @Bean
    public Queue ticketBookingSagaDeadLetterQueue() {
        return new Queue(TICKET_BOOKING_SAGA_DLQ, true);
    }

    @Bean
    public Binding bookingPlacedBinding(Queue ticketBookingSagaQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(ticketBookingSagaQueue)
                .to(bookingExchange)
                .with(BOOKING_PLACED_ROUTING_KEY);
    }

    @Bean
    public Binding bookingCompletedBinding(Queue ticketBookingSagaQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(ticketBookingSagaQueue)
                .to(bookingExchange)
                .with(BOOKING_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding bookingCancelledBinding(Queue ticketBookingSagaQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(ticketBookingSagaQueue)
                .to(bookingExchange)
                .with(BOOKING_CANCELLED_ROUTING_KEY);
    }

    @Bean
    public Binding ticketBookingSagaDeadLetterBinding(
            Queue ticketBookingSagaDeadLetterQueue,
            TopicExchange ticketBookingSagaDeadLetterExchange
    ) {
        return BindingBuilder.bind(ticketBookingSagaDeadLetterQueue)
                .to(ticketBookingSagaDeadLetterExchange)
                .with(TICKET_BOOKING_SAGA_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
