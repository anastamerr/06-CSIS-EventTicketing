package com.team06.eventticketing.event.messaging;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventEventConfig {

    public static final String EVENT_EXCHANGE = "event.events";
    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String EVENT_BOOKING_QUEUE = "event.booking.saga-listener";
    public static final String EVENT_BOOKING_DLQ = "event.booking.saga-listener.dlq";
    public static final String EVENT_BOOKING_DLQ_EXCHANGE = "event.booking.saga-listener.dlx";
    public static final String EVENT_BOOKING_DLQ_ROUTING_KEY = "event.booking.saga-listener.dead";
    public static final String STATUS_CHANGED_ROUTING_KEY = "event.status-changed";
    public static final String RATED_ROUTING_KEY = "event.rated";

    @Bean
    TopicExchange eventExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange eventBookingDeadLetterExchange() {
        return new TopicExchange(EVENT_BOOKING_DLQ_EXCHANGE, true, false);
    }

    @Bean
    Queue eventBookingSagaQueue() {
        return new Queue(EVENT_BOOKING_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", EVENT_BOOKING_DLQ_EXCHANGE,
                "x-dead-letter-routing-key", EVENT_BOOKING_DLQ_ROUTING_KEY));
    }

    @Bean
    Queue eventBookingSagaDeadLetterQueue() {
        return new Queue(EVENT_BOOKING_DLQ, true);
    }

    @Bean
    Binding bookingPlacedBinding(Queue eventBookingSagaQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(eventBookingSagaQueue).to(bookingExchange).with("booking.placed");
    }

    @Bean
    Binding bookingCompletedBinding(Queue eventBookingSagaQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(eventBookingSagaQueue).to(bookingExchange).with("booking.completed");
    }

    @Bean
    Binding bookingCancelledBinding(Queue eventBookingSagaQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(eventBookingSagaQueue).to(bookingExchange).with("booking.cancelled");
    }

    @Bean
    Binding eventBookingDeadLetterBinding(Queue eventBookingSagaDeadLetterQueue, TopicExchange eventBookingDeadLetterExchange) {
        return BindingBuilder.bind(eventBookingSagaDeadLetterQueue)
                .to(eventBookingDeadLetterExchange)
                .with(EVENT_BOOKING_DLQ_ROUTING_KEY);
    }

    @Bean
    MessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
