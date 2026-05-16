package com.team06.eventticketing.booking.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.team06.eventticketing.booking.service.BookingService;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.events.PaymentFailedEvent;
import com.team06.eventticketing.contracts.events.PaymentInitiatedEvent;
import com.team06.eventticketing.contracts.events.TicketStatusChangedEvent;
import com.team06.eventticketing.contracts.messaging.EventTicketingMessagingContracts;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BookingSagaE2ERabbitMqBonusTest {

    private static final String SALES_SIM_QUEUE = "bonus.sales.sim";
    private static final String TICKET_SIM_QUEUE = "bonus.ticket.sim";
    private static final String PAYMENT_OBSERVED_QUEUE = "bonus.payment.observed";
    private static final String TICKET_OBSERVED_QUEUE = "bonus.ticket.observed";
    private static final String TICKET_STATUS_CHANGED_ROUTING_KEY = "ticket.status-changed";

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    private final List<SimpleMessageListenerContainer> listenerContainers = new ArrayList<>();
    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private MessageConverter messageConverter;
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
        listenerContainers.forEach(container -> {
            container.stop();
            container.destroy();
        });
        connectionFactory.destroy();
    }

    @Test
    void sagaPublishesPaymentAndTicketEventsThenCompensatesOnPaymentFailure() {
        BookingSagaFeedbackConsumer feedbackConsumer = new BookingSagaFeedbackConsumer(bookingService);
        startListener(SALES_SIM_QUEUE, this::simulateSalesConsumer);
        startListener(TICKET_SIM_QUEUE, this::simulateTicketConsumer);
        startListener(BookingEventConfig.BOOKING_SAGA_FEEDBACK_QUEUE, message -> feedbackConsumer.consumeSagaFeedback(
                messageConverter.fromMessage(message),
                message.getMessageProperties().getReceivedRoutingKey(),
                message.getMessageProperties().getHeader("correlationId")));

        rabbitTemplate.convertAndSend(
                BookingEventConfig.BOOKING_EXCHANGE,
                EventTicketingMessagingContracts.BOOKING_COMPLETED_ROUTING_KEY,
                new BookingCompletedEvent(90L, 91L, 92L, new BigDecimal("300.00")));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message paymentMessage = rabbitTemplate.receive(PAYMENT_OBSERVED_QUEUE);
            assertThat(paymentMessage).isNotNull();
            assertThat(messageConverter.fromMessage(paymentMessage)).isInstanceOf(PaymentInitiatedEvent.class);
        });
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Message ticketMessage = rabbitTemplate.receive(TICKET_OBSERVED_QUEUE);
            assertThat(ticketMessage).isNotNull();
            assertThat(messageConverter.fromMessage(ticketMessage)).isInstanceOf(TicketStatusChangedEvent.class);
        });

        rabbitTemplate.convertAndSend(
                BookingEventConfig.PAYMENT_EXCHANGE,
                EventTicketingMessagingContracts.PAYMENT_FAILED_ROUTING_KEY,
                new PaymentFailedEvent(90L, 93L, "processor_declined"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(bookingService).markPaymentFailed(90L, "processor_declined"));
    }

    private void simulateSalesConsumer(Message message) {
        BookingCompletedEvent event = (BookingCompletedEvent) messageConverter.fromMessage(message);
        rabbitTemplate.convertAndSend(
                BookingEventConfig.PAYMENT_EXCHANGE,
                EventTicketingMessagingContracts.PAYMENT_INITIATED_ROUTING_KEY,
                new PaymentInitiatedEvent(93L, event.bookingId(), event.totalAmount()));
    }

    private void simulateTicketConsumer(Message message) {
        BookingCompletedEvent event = (BookingCompletedEvent) messageConverter.fromMessage(message);
        rabbitTemplate.convertAndSend(
                BookingEventConfig.TICKET_EXCHANGE,
                TICKET_STATUS_CHANGED_ROUTING_KEY,
                new TicketStatusChangedEvent(94L, event.bookingId(), "ISSUED"));
    }

    private void startListener(String queueName, org.springframework.amqp.core.MessageListener listener) {
        SimpleMessageListenerContainer listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
        listenerContainer.setQueueNames(queueName);
        listenerContainer.setMessageListener(listener);
        listenerContainer.setDefaultRequeueRejected(false);
        listenerContainer.start();
        listenerContainers.add(listenerContainer);
        await().atMost(Duration.ofSeconds(5)).until(listenerContainer::isActive);
    }

    private void declareTopology(AmqpAdmin admin) {
        TopicExchange bookingExchange = new TopicExchange(BookingEventConfig.BOOKING_EXCHANGE, true, false);
        TopicExchange paymentExchange = new TopicExchange(BookingEventConfig.PAYMENT_EXCHANGE, true, false);
        TopicExchange ticketExchange = new TopicExchange(BookingEventConfig.TICKET_EXCHANGE, true, false);
        Queue salesQueue = new Queue(SALES_SIM_QUEUE, true);
        Queue ticketQueue = new Queue(TICKET_SIM_QUEUE, true);
        Queue feedbackQueue = new Queue(BookingEventConfig.BOOKING_SAGA_FEEDBACK_QUEUE, true);
        Queue paymentObservedQueue = new Queue(PAYMENT_OBSERVED_QUEUE, true);
        Queue ticketObservedQueue = new Queue(TICKET_OBSERVED_QUEUE, true);

        admin.declareExchange(bookingExchange);
        admin.declareExchange(paymentExchange);
        admin.declareExchange(ticketExchange);
        admin.declareQueue(salesQueue);
        admin.declareQueue(ticketQueue);
        admin.declareQueue(feedbackQueue);
        admin.declareQueue(paymentObservedQueue);
        admin.declareQueue(ticketObservedQueue);
        admin.declareBinding(BindingBuilder.bind(salesQueue)
                .to(bookingExchange)
                .with(EventTicketingMessagingContracts.BOOKING_COMPLETED_ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(ticketQueue)
                .to(bookingExchange)
                .with(EventTicketingMessagingContracts.BOOKING_COMPLETED_ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(feedbackQueue)
                .to(paymentExchange)
                .with(EventTicketingMessagingContracts.PAYMENT_FAILED_ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(paymentObservedQueue)
                .to(paymentExchange)
                .with(EventTicketingMessagingContracts.PAYMENT_INITIATED_ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(ticketObservedQueue)
                .to(ticketExchange)
                .with(TICKET_STATUS_CHANGED_ROUTING_KEY));
    }
}
