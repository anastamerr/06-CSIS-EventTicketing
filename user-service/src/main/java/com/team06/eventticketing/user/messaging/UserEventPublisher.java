package com.team06.eventticketing.user.messaging;

import com.team06.eventticketing.contracts.events.UserDeactivatedEvent;
import com.team06.eventticketing.contracts.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUserRegistered(UserRegisteredEvent event) {
        publish(UserEventConfig.USER_REGISTERED_ROUTING_KEY, event, event.userId());
    }

    public void publishUserDeactivated(UserDeactivatedEvent event) {
        publish(UserEventConfig.USER_DEACTIVATED_ROUTING_KEY, event, event.userId());
    }

    private void publish(String routingKey, Object event, Long userId) {
        MDC.put("routingKey", routingKey);
        if (userId != null) {
            MDC.put("userId", userId.toString());
        }
        try {
            rabbitTemplate.convertAndSend(
                    UserEventConfig.USER_EVENTS_EXCHANGE,
                    routingKey,
                    event);
            log.info("Published {} event for userId={}", routingKey, userId);
        } finally {
            MDC.remove("routingKey");
            MDC.remove("userId");
        }
    }
}
