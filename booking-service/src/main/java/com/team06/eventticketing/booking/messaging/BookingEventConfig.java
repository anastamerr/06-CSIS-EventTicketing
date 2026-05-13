package com.team06.eventticketing.booking.messaging;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingEventConfig {

    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String PAYMENT_EXCHANGE = "payment.events";
    public static final String TICKET_EXCHANGE = "ticket.events";
    public static final String BOOKING_SAGA_FEEDBACK_QUEUE = "booking.saga-feedback";
    public static final String BOOKING_SAGA_FEEDBACK_DLQ = "booking.saga-feedback.dlq";
    public static final String BOOKING_SAGA_FEEDBACK_DLX = "booking.saga-feedback.dlx";
    public static final String BOOKING_SAGA_FEEDBACK_DLQ_ROUTING_KEY = "booking.saga-feedback.dead";

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange ticketExchange() {
        return new TopicExchange(TICKET_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange bookingSagaFeedbackDeadLetterExchange() {
        return new TopicExchange(BOOKING_SAGA_FEEDBACK_DLX, true, false);
    }

    @Bean
    public Queue bookingSagaFeedbackQueue() {
        return new Queue(BOOKING_SAGA_FEEDBACK_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", BOOKING_SAGA_FEEDBACK_DLX,
                "x-dead-letter-routing-key", BOOKING_SAGA_FEEDBACK_DLQ_ROUTING_KEY));
    }

    @Bean
    public Queue bookingSagaFeedbackDeadLetterQueue() {
        return new Queue(BOOKING_SAGA_FEEDBACK_DLQ, true);
    }

    @Bean
    public Binding paymentInitiatedBinding(
            @Qualifier("bookingSagaFeedbackQueue") Queue bookingSagaFeedbackQueue,
            @Qualifier("paymentExchange") TopicExchange paymentExchange
    ) {
        return BindingBuilder.bind(bookingSagaFeedbackQueue).to(paymentExchange).with("payment.initiated");
    }

    @Bean
    public Binding paymentCompletedBinding(
            @Qualifier("bookingSagaFeedbackQueue") Queue bookingSagaFeedbackQueue,
            @Qualifier("paymentExchange") TopicExchange paymentExchange
    ) {
        return BindingBuilder.bind(bookingSagaFeedbackQueue).to(paymentExchange).with("payment.completed");
    }

    @Bean
    public Binding paymentFailedBinding(
            @Qualifier("bookingSagaFeedbackQueue") Queue bookingSagaFeedbackQueue,
            @Qualifier("paymentExchange") TopicExchange paymentExchange
    ) {
        return BindingBuilder.bind(bookingSagaFeedbackQueue).to(paymentExchange).with("payment.failed");
    }

    @Bean
    public Binding paymentRefundedBinding(
            @Qualifier("bookingSagaFeedbackQueue") Queue bookingSagaFeedbackQueue,
            @Qualifier("paymentExchange") TopicExchange paymentExchange
    ) {
        return BindingBuilder.bind(bookingSagaFeedbackQueue).to(paymentExchange).with("payment.refunded");
    }

    @Bean
    public Binding ticketIssuedBinding(
            @Qualifier("bookingSagaFeedbackQueue") Queue bookingSagaFeedbackQueue,
            @Qualifier("ticketExchange") TopicExchange ticketExchange
    ) {
        return BindingBuilder.bind(bookingSagaFeedbackQueue).to(ticketExchange).with("ticket.issued");
    }

    @Bean
    public Binding bookingSagaFeedbackDeadLetterBinding(
            @Qualifier("bookingSagaFeedbackDeadLetterQueue") Queue bookingSagaFeedbackDeadLetterQueue,
            @Qualifier("bookingSagaFeedbackDeadLetterExchange") TopicExchange bookingSagaFeedbackDeadLetterExchange
    ) {
        return BindingBuilder.bind(bookingSagaFeedbackDeadLetterQueue)
                .to(bookingSagaFeedbackDeadLetterExchange)
                .with(BOOKING_SAGA_FEEDBACK_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
