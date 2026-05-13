package com.team06.eventticketing.sales.messaging;

import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.sales.service.TicketSaleService;
import java.math.BigDecimal;
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
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);

    private final TicketSaleService ticketSaleService;

    public BookingEventConsumer(TicketSaleService ticketSaleService) {
        this.ticketSaleService = ticketSaleService;
    }

    @RabbitListener(queues = PaymentEventConfig.PAYMENT_SAGA_QUEUE)
    public void consumeBookingEvent(
            @Payload Object event,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey
    ) {
        MDC.put("routingKey", routingKey);
        try {
            if ("booking.completed".equals(routingKey) && event instanceof BookingCompletedEvent completedEvent) {
                ticketSaleService.handleBookingCompleted(completedEvent);
                log.info("Processed booking.completed and published payment.initiated");
            } else if ("booking.completed".equals(routingKey) && event instanceof Map<?, ?> map) {
                ticketSaleService.handleBookingCompleted(toBookingCompletedEvent(map));
                log.info("Processed booking.completed and published payment.initiated");
            } else if ("booking.cancelled".equals(routingKey) && event instanceof BookingCancelledEvent cancelledEvent) {
                ticketSaleService.handleBookingCancelled(cancelledEvent);
                log.info("Processed booking.cancelled and published refund when applicable");
            } else if ("booking.cancelled".equals(routingKey) && event instanceof Map<?, ?> map) {
                ticketSaleService.handleBookingCancelled(toBookingCancelledEvent(map));
                log.info("Processed booking.cancelled and published refund when applicable");
            } else {
                log.warn("Ignoring unsupported booking event type {}", event == null ? "null" : event.getClass().getName());
            }
        } finally {
            MDC.remove("routingKey");
        }
    }

    private BookingCompletedEvent toBookingCompletedEvent(Map<?, ?> map) {
        return new BookingCompletedEvent(
                longValue(map.get("bookingId")),
                longValue(map.get("userId")),
                longValue(map.get("eventId")),
                bigDecimalValue(map.get("totalAmount")));
    }

    private BookingCancelledEvent toBookingCancelledEvent(Map<?, ?> map) {
        return new BookingCancelledEvent(
                longValue(map.get("bookingId")),
                longValue(map.get("userId")),
                longValue(map.get("eventId")),
                stringValue(map.get("reason")));
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private BigDecimal bigDecimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
