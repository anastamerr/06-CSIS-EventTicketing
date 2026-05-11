package com.team06.eventticketing.user.messaging;

import com.team06.eventticketing.contracts.events.UserDeactivatedEvent;
import com.team06.eventticketing.contracts.events.UserRegisteredEvent;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUserRegistered(UserRegisteredEvent event) {
        MDC.put("routingKey", "user.registered");
        try {
            rabbitTemplate.convertAndSend(UserEventConfig.USER_EXCHANGE, "user.registered", event);
        } finally {
            MDC.remove("routingKey");
        }
    }

    public void publishUserDeactivated(UserDeactivatedEvent event) {
        MDC.put("routingKey", "user.deactivated");
        try {
            rabbitTemplate.convertAndSend(UserEventConfig.USER_EXCHANGE, "user.deactivated", event);
        } finally {
            MDC.remove("routingKey");
        }
    }
}
