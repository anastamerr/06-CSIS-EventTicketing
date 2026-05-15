package com.team06.eventticketing.ticket.messaging;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.ticket.service.TicketService;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BookingSagaRabbitMqIntegrationTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private MessageConverter messageConverter;
    private SimpleMessageListenerContainer listenerContainer;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());

        messageConverter = new JacksonJsonMessageConverter();
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);

        declareTicketBookingSagaTopology(new RabbitAdmin(connectionFactory));
        ticketService = mock(TicketService.class);
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
    void consumesBookingPlacedFromRealRabbitMqQueue() {
        BookingSagaConsumer consumer = new BookingSagaConsumer(ticketService, new ObjectMapper());
        startListener(message -> consumer.consumeBookingEvent(
                messageConverter.fromMessage(message),
                message.getMessageProperties().getReceivedRoutingKey(),
                message.getMessageProperties().getHeader("correlationId")));

        rabbitTemplate.convertAndSend(
                TicketEventConfig.BOOKING_EXCHANGE,
                TicketEventConfig.BOOKING_PLACED_ROUTING_KEY,
                new BookingPlacedEvent(55L, 9L, 77L),
                message -> {
                    message.getMessageProperties().setHeader("correlationId", "tc-corr-1");
                    return message;
                });

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(ticketService).captureEventIdForBooking(55L, 77L));
    }

    @Test
    void rejectedConsumerExceptionsRouteToDeadLetterQueueWithoutManualAck() {
        startListener(message -> {
            throw new IllegalStateException("force listener failure");
        });

        rabbitTemplate.convertAndSend(
                TicketEventConfig.BOOKING_EXCHANGE,
                TicketEventConfig.BOOKING_PLACED_ROUTING_KEY,
                new BookingPlacedEvent(56L, 10L, 78L));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message deadLetter = rabbitTemplate.receive(TicketEventConfig.TICKET_BOOKING_SAGA_DLQ);
            org.assertj.core.api.Assertions.assertThat(deadLetter).isNotNull();
        });
    }

    private void startListener(org.springframework.amqp.core.MessageListener listener) {
        listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
        listenerContainer.setQueueNames(TicketEventConfig.TICKET_BOOKING_SAGA_QUEUE);
        listenerContainer.setMessageListener(listener);
        listenerContainer.setDefaultRequeueRejected(false);
        listenerContainer.start();
        await().atMost(Duration.ofSeconds(5)).until(listenerContainer::isActive);
    }

    private void declareTicketBookingSagaTopology(AmqpAdmin admin) {
        TopicExchange bookingExchange = new TopicExchange(TicketEventConfig.BOOKING_EXCHANGE, true, false);
        TopicExchange deadLetterExchange = new TopicExchange(TicketEventConfig.TICKET_BOOKING_SAGA_DLX, true, false);
        Queue sagaQueue = new TicketEventConfig().ticketBookingSagaQueue();
        Queue deadLetterQueue = new Queue(TicketEventConfig.TICKET_BOOKING_SAGA_DLQ, true);

        admin.declareExchange(bookingExchange);
        admin.declareExchange(deadLetterExchange);
        admin.declareQueue(sagaQueue);
        admin.declareQueue(deadLetterQueue);
        admin.declareBinding(BindingBuilder.bind(sagaQueue)
                .to(bookingExchange)
                .with(TicketEventConfig.BOOKING_PLACED_ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(TicketEventConfig.TICKET_BOOKING_SAGA_DLQ_ROUTING_KEY));
    }
}
