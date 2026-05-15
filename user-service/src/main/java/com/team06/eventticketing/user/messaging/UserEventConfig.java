package com.team06.eventticketing.user.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventConfig {

    public static final String USER_EXCHANGE = "user.events";
    public static final String USER_EVENTS_EXCHANGE = USER_EXCHANGE;
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String USER_DEACTIVATED_ROUTING_KEY = "user.deactivated";
    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String BOOKING_DLX = "booking.events.dlx";
    public static final String USER_BOOKING_SAGA_QUEUE = "user.booking.saga-listener";
    public static final String USER_BOOKING_SAGA_DLQ = "user.booking.saga-listener.dlq";

    @Bean
    public TopicExchange userExchange() {
        return new TopicExchange(USER_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange bookingExchangeRef() {
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange bookingDeadLetterExchange() {
        return new TopicExchange(BOOKING_DLX, true, false);
    }

    @Bean
    public Queue userBookingSagaQueue() {
        return QueueBuilder.durable(USER_BOOKING_SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", BOOKING_DLX)
                .withArgument("x-dead-letter-routing-key", USER_BOOKING_SAGA_DLQ)
                .build();
    }

    @Bean
    public Queue userBookingSagaDlq() {
        return QueueBuilder.durable(USER_BOOKING_SAGA_DLQ).build();
    }

    @Bean
    public Binding userBookingSagaDlqBinding() {
        return BindingBuilder.bind(userBookingSagaDlq())
                .to(bookingDeadLetterExchange())
                .with(USER_BOOKING_SAGA_DLQ);
    }

    @Bean
    public Binding bookingCompletedBinding() {
        return BindingBuilder.bind(userBookingSagaQueue())
                .to(bookingExchangeRef())
                .with("booking.completed");
    }

    @Bean
    public Binding bookingCancelledBinding() {
        return BindingBuilder.bind(userBookingSagaQueue())
                .to(bookingExchangeRef())
                .with("booking.cancelled");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
