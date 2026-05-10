package com.team06.eventticketing.event.messaging;

import com.team06.eventticketing.contracts.events.EventRatedEvent;
import com.team06.eventticketing.contracts.events.EventStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishStatusChanged(EventStatusChangedEvent event) {
        publish(EventEventConfig.STATUS_CHANGED_ROUTING_KEY, event);
    }

    public void publishRated(EventRatedEvent event) {
        publish(EventEventConfig.RATED_ROUTING_KEY, event);
    }

    private void publish(String routingKey, Object event) {
        MDC.put("routingKey", routingKey);
        try {
            String correlationId = MDC.get("correlationId");
            rabbitTemplate.convertAndSend(EventEventConfig.EVENT_EXCHANGE, routingKey, event, message -> {
                if (correlationId != null && !correlationId.isBlank()) {
                    message.getMessageProperties().setHeader("correlationId", correlationId);
                }
                return message;
            });
            log.info("Published event-service RabbitMQ event");
        } finally {
            MDC.remove("routingKey");
        }
    }
}
