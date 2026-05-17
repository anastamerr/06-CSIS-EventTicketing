package com.team06.eventticketing.sales.messaging;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.sales.service.TicketSaleService;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.opentest4j.TestAbortedException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.testcontainers.containers.RabbitMQContainer;

@DisabledIfEnvironmentVariable(named = "TESTCONTAINERS_DISABLED", matches = "true")
class BookingEventConsumerRabbitMqIntegrationTest {

    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private MessageConverter messageConverter;
    private SimpleMessageListenerContainer listenerContainer;
    private TicketSaleService ticketSaleService;

    @BeforeAll
    static void startRabbitMq() {
        try {
            RABBIT.start();
        } catch (RuntimeException ex) {
            throw new TestAbortedException("Docker is not available for RabbitMQ integration test", ex);
        }
    }

    @AfterAll
    static void stopRabbitMq() {
        RABBIT.stop();
    }

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());

        messageConverter = new JacksonJsonMessageConverter();
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        declareTopology(new RabbitAdmin(connectionFactory));
        ticketSaleService = mock(TicketSaleService.class);
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
    void consumesBookingCompletedFromRealRabbitMqQueue() {
        BookingEventConsumer consumer = new BookingEventConsumer(ticketSaleService, new ObjectMapper());
        startListener(message -> consumer.consumeBookingEvent(
                messageConverter.fromMessage(message),
                message.getMessageProperties().getReceivedRoutingKey()));

        BookingCompletedEvent event = new BookingCompletedEvent(70L, 8L, 9L, new BigDecimal("120.00"));
        rabbitTemplate.convertAndSend(PaymentEventConfig.BOOKING_EXCHANGE, "booking.completed", event);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(ticketSaleService).handleBookingCompleted(event));
    }

    private void startListener(org.springframework.amqp.core.MessageListener listener) {
        listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
        listenerContainer.setQueueNames(PaymentEventConfig.PAYMENT_SAGA_QUEUE);
        listenerContainer.setMessageListener(listener);
        listenerContainer.setDefaultRequeueRejected(false);
        listenerContainer.start();
        await().atMost(Duration.ofSeconds(5)).until(listenerContainer::isActive);
    }

    private void declareTopology(AmqpAdmin admin) {
        TopicExchange bookingExchange = new TopicExchange(PaymentEventConfig.BOOKING_EXCHANGE, true, false);
        Queue sagaQueue = new Queue(PaymentEventConfig.PAYMENT_SAGA_QUEUE, true);

        admin.declareExchange(bookingExchange);
        admin.declareQueue(sagaQueue);
        admin.declareBinding(BindingBuilder.bind(sagaQueue).to(bookingExchange).with("booking.completed"));
    }
}
