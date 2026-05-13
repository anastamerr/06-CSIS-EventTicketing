package com.team06.eventticketing.user.messaging;

import static org.mockito.Mockito.verify;

import com.team06.eventticketing.contracts.events.UserDeactivatedEvent;
import com.team06.eventticketing.contracts.events.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishUserRegisteredSendsToUserEventsExchangeWithRegisteredRoutingKey() {
        UserEventPublisher publisher = new UserEventPublisher(rabbitTemplate);
        UserRegisteredEvent event = new UserRegisteredEvent(7L, "nora@example.com", "ATTENDEE");

        publisher.publishUserRegistered(event);

        verify(rabbitTemplate).convertAndSend(
                UserEventConfig.USER_EVENTS_EXCHANGE,
                UserEventConfig.USER_REGISTERED_ROUTING_KEY,
                event);
    }

    @Test
    void publishUserDeactivatedSendsToUserEventsExchangeWithDeactivatedRoutingKey() {
        UserEventPublisher publisher = new UserEventPublisher(rabbitTemplate);
        UserDeactivatedEvent event = new UserDeactivatedEvent(5L);

        publisher.publishUserDeactivated(event);

        verify(rabbitTemplate).convertAndSend(
                UserEventConfig.USER_EVENTS_EXCHANGE,
                UserEventConfig.USER_DEACTIVATED_ROUTING_KEY,
                event);
    }
}
