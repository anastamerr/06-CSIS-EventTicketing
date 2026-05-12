package com.team06.eventticketing.sales.messaging;

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
public class PaymentEventConfig {

    public static final String PAYMENT_EXCHANGE = "payment.events";
    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String PAYMENT_SAGA_QUEUE = "payment.saga-listener";
    public static final String PAYMENT_SAGA_DLQ = "payment.saga-listener.dlq";
    public static final String PAYMENT_SAGA_DLQ_EXCHANGE = "payment.saga-listener.dlx";
    public static final String PAYMENT_SAGA_DLQ_ROUTING_KEY = "payment.saga-listener.dead";
    public static final String PAYMENT_INITIATED_ROUTING_KEY = "payment.initiated";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";
    public static final String PAYMENT_REFUNDED_ROUTING_KEY = "payment.refunded";

    @Bean
    TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange paymentSagaDeadLetterExchange() {
        return new TopicExchange(PAYMENT_SAGA_DLQ_EXCHANGE, true, false);
    }

    @Bean
    Queue paymentSagaQueue() {
        return new Queue(PAYMENT_SAGA_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", PAYMENT_SAGA_DLQ_EXCHANGE,
                "x-dead-letter-routing-key", PAYMENT_SAGA_DLQ_ROUTING_KEY));
    }

    @Bean
    Queue paymentSagaDeadLetterQueue() {
        return new Queue(PAYMENT_SAGA_DLQ, true);
    }

    @Bean
    Binding bookingCompletedBinding(Queue paymentSagaQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(paymentSagaQueue).to(bookingExchange).with("booking.completed");
    }

    @Bean
    Binding bookingCancelledBinding(Queue paymentSagaQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(paymentSagaQueue).to(bookingExchange).with("booking.cancelled");
    }

    @Bean
    Binding paymentSagaDeadLetterBinding(Queue paymentSagaDeadLetterQueue, TopicExchange paymentSagaDeadLetterExchange) {
        return BindingBuilder.bind(paymentSagaDeadLetterQueue)
                .to(paymentSagaDeadLetterExchange)
                .with(PAYMENT_SAGA_DLQ_ROUTING_KEY);
    }

    @Bean
    MessageConverter rabbitJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
