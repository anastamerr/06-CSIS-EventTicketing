package com.team06.eventticketing.ticket.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketEventConfig {

    // Our own outgoing exchange — ticket events we publish
    public static final String TICKET_EXCHANGE = "ticket.events";

    // External exchange we consume from — owned by booking-service
    public static final String BOOKING_EXCHANGE = "booking.events";

    // Our queue that receives booking.placed events
    public static final String TICKET_BOOKING_PLACED_QUEUE = "ticket.booking-placed";

    @Bean
    public TopicExchange ticketExchange() {
        return new TopicExchange(TICKET_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange bookingExchangeReference() {
        // Declared as a durable topic exchange so it exists even if ticket-service
        // starts before booking-service. RabbitMQ allows redeclaring with same args.
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    public Queue ticketBookingPlacedQueue() {
        return QueueBuilder.durable(TICKET_BOOKING_PLACED_QUEUE).build();
    }

    @Bean
    public Binding ticketBookingPlacedBinding(Queue ticketBookingPlacedQueue,
                                              TopicExchange bookingExchangeReference) {
        return BindingBuilder.bind(ticketBookingPlacedQueue)
                .to(bookingExchangeReference)
                .with("booking.placed");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}