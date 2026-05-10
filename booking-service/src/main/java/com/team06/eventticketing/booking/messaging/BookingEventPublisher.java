package com.team06.eventticketing.booking.messaging;

import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishBookingPlaced(BookingPlacedEvent event) {
        rabbitTemplate.convertAndSend(BookingEventConfig.BOOKING_EXCHANGE, "booking.placed", event);
    }

    public void publishBookingCompleted(BookingCompletedEvent event) {
        rabbitTemplate.convertAndSend(BookingEventConfig.BOOKING_EXCHANGE, "booking.completed", event);
    }

    public void publishBookingCancelled(BookingCancelledEvent event) {
        rabbitTemplate.convertAndSend(BookingEventConfig.BOOKING_EXCHANGE, "booking.cancelled", event);
    }
}
