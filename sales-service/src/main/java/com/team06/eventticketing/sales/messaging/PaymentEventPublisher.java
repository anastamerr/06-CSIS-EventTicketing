package com.team06.eventticketing.sales.messaging;

import com.team06.eventticketing.contracts.events.PaymentCompletedEvent;
import com.team06.eventticketing.contracts.events.PaymentFailedEvent;
import com.team06.eventticketing.contracts.events.PaymentInitiatedEvent;
import com.team06.eventticketing.contracts.events.PaymentRefundedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        rabbitTemplate.convertAndSend(PaymentEventConfig.PAYMENT_EXCHANGE,
                PaymentEventConfig.PAYMENT_INITIATED_ROUTING_KEY, event);
        logPublished(PaymentEventConfig.PAYMENT_INITIATED_ROUTING_KEY, event.bookingId(), event.saleId());
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        rabbitTemplate.convertAndSend(PaymentEventConfig.PAYMENT_EXCHANGE,
                PaymentEventConfig.PAYMENT_COMPLETED_ROUTING_KEY, event);
        logPublished(PaymentEventConfig.PAYMENT_COMPLETED_ROUTING_KEY, event.bookingId(), event.saleId());
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        rabbitTemplate.convertAndSend(PaymentEventConfig.PAYMENT_EXCHANGE,
                PaymentEventConfig.PAYMENT_FAILED_ROUTING_KEY, event);
        logPublished(PaymentEventConfig.PAYMENT_FAILED_ROUTING_KEY, event.bookingId(), event.saleId());
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        rabbitTemplate.convertAndSend(PaymentEventConfig.PAYMENT_EXCHANGE,
                PaymentEventConfig.PAYMENT_REFUNDED_ROUTING_KEY, event);
        logPublished(PaymentEventConfig.PAYMENT_REFUNDED_ROUTING_KEY, event.bookingId(), event.saleId());
    }

    private void logPublished(String routingKey, Long bookingId, Long saleId) {
        MDC.put("routingKey", routingKey);
        if (bookingId != null) {
            MDC.put("bookingId", bookingId.toString());
        }
        if (saleId != null) {
            MDC.put("saleId", saleId.toString());
        }
        try {
            log.info("Published {}", routingKey);
        } finally {
            MDC.remove("saleId");
            MDC.remove("bookingId");
            MDC.remove("routingKey");
        }
    }
}
