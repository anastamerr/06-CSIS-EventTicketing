package com.team06.eventticketing.booking.messaging;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.team06.eventticketing.booking.service.BookingService;
import com.team06.eventticketing.contracts.events.PaymentFailedEvent;
import com.team06.eventticketing.contracts.messaging.EventTicketingMessagingContracts;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BookingSagaFeedbackRabbitMqIntegrationTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private MessageConverter messageConverter;
    private SimpleMessageListenerContainer listenerContainer;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());

        messageConverter = new Jackson2JsonMessageConverter();
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        declareTopology(new RabbitAdmin(connectionFactory));
        bookingService = mock(BookingService.class);
    }

    @AfterEach
    void tearDown() {
        if (listenerContainer != null) {
            listenerContainer.stop();
            listenerContainer.destroy();
        }
        connectionFactory.destroy();
    }

    @Test
    void consumesPaymentFailedFromRealRabbitMqQueueAndRunsCompensationHandler() {
        BookingSagaFeedbackConsumer consumer = new BookingSagaFeedbackConsumer(bookingService);
        startListener(message -> consumer.consumeSagaFeedback(
                messageConverter.fromMessage(message),
                message.getMessageProperties().getReceivedRoutingKey(),
                message.getMessageProperties().getHeader("correlationId")));

        rabbitTemplate.convertAndSend(
                BookingEventConfig.PAYMENT_EXCHANGE,
                EventTicketingMessagingContracts.PAYMENT_FAILED_ROUTING_KEY,
                new PaymentFailedEvent(80L, 81L, "card_declined"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(bookingService).markPaymentFailed(80L, "card_declined"));
    }

    private void startListener(org.springframework.amqp.core.MessageListener listener) {
        listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
        listenerContainer.setQueueNames(BookingEventConfig.BOOKING_SAGA_FEEDBACK_QUEUE);
        listenerContainer.setMessageListener(listener);
        listenerContainer.setDefaultRequeueRejected(false);
        listenerContainer.start();
        await().atMost(Duration.ofSeconds(5)).until(listenerContainer::isActive);
    }

    private void declareTopology(AmqpAdmin admin) {
        TopicExchange paymentExchange = new TopicExchange(BookingEventConfig.PAYMENT_EXCHANGE, true, false);
        Queue feedbackQueue = new Queue(BookingEventConfig.BOOKING_SAGA_FEEDBACK_QUEUE, true);

        admin.declareExchange(paymentExchange);
        admin.declareQueue(feedbackQueue);
        admin.declareBinding(BindingBuilder.bind(feedbackQueue)
                .to(paymentExchange)
                .with(EventTicketingMessagingContracts.PAYMENT_FAILED_ROUTING_KEY));
    }
}
