package com.team06.eventticketing.booking.messaging;

import com.team06.eventticketing.booking.service.BookingService;
import com.team06.eventticketing.contracts.events.PaymentCompletedEvent;
import com.team06.eventticketing.contracts.events.PaymentFailedEvent;
import com.team06.eventticketing.contracts.events.PaymentInitiatedEvent;
import com.team06.eventticketing.contracts.events.PaymentRefundedEvent;
import com.team06.eventticketing.contracts.events.TicketIssuedEvent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class BookingSagaFeedbackConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingSagaFeedbackConsumer.class);

    private final BookingService bookingService;

    public BookingSagaFeedbackConsumer(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @RabbitListener(queues = BookingEventConfig.BOOKING_SAGA_FEEDBACK_QUEUE)
    public void consumeSagaFeedback(
            @Payload Object event,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(name = "correlationId", required = false) String correlationId
    ) {
        Long bookingId = extractBookingId(event);
        putMdc(bookingId, routingKey, correlationId);
        try {
            switch (routingKey) {
                case "payment.initiated" -> bookingService.markPaymentInitiated(bookingId);
                case "payment.completed" -> bookingService.markPaymentCompleted(bookingId);
                case "payment.failed" -> bookingService.markPaymentFailed(bookingId, extractReason(event));
                case "payment.refunded" -> bookingService.markPaymentRefunded(bookingId);
                case "ticket.issued" -> log.info("Consumed ticket.issued saga feedback for booking {}", bookingId);
                default -> log.warn("Ignoring unsupported booking saga feedback routing key {}", routingKey);
            }
        } finally {
            MDC.remove("bookingId");
            MDC.remove("routingKey");
            MDC.remove("correlationId");
        }
    }

    private void putMdc(Long bookingId, String routingKey, String correlationId) {
        if (bookingId != null) {
            MDC.put("bookingId", bookingId.toString());
        }
        MDC.put("routingKey", routingKey);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put("correlationId", correlationId);
        }
    }

    private Long extractBookingId(Object event) {
        if (event instanceof PaymentInitiatedEvent paymentInitiatedEvent) {
            return paymentInitiatedEvent.bookingId();
        }
        if (event instanceof PaymentCompletedEvent paymentCompletedEvent) {
            return paymentCompletedEvent.bookingId();
        }
        if (event instanceof PaymentFailedEvent paymentFailedEvent) {
            return paymentFailedEvent.bookingId();
        }
        if (event instanceof PaymentRefundedEvent paymentRefundedEvent) {
            return paymentRefundedEvent.bookingId();
        }
        if (event instanceof TicketIssuedEvent ticketIssuedEvent) {
            return ticketIssuedEvent.bookingId();
        }
        if (event instanceof Map<?, ?> map) {
            return toLong(map.get("bookingId"));
        }
        throw new IllegalArgumentException("Saga feedback event is missing bookingId");
    }

    private String extractReason(Object event) {
        if (event instanceof PaymentFailedEvent paymentFailedEvent) {
            return paymentFailedEvent.reason();
        }
        if (event instanceof Map<?, ?> map) {
            Object reason = map.get("reason");
            return reason == null ? "payment_failed" : reason.toString();
        }
        return "payment_failed";
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            return Long.valueOf(value.toString());
        }
        return null;
    }
}
