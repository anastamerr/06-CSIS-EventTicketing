package com.team06.eventticketing.booking.service;

import com.team06.eventticketing.booking.model.Booking;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishBookingPlaced(Booking booking) {
        Map<String, Object> payload = Map.of(
                "bookingId", booking.getId(),
                "userId", booking.getUserId(),
                "eventId", booking.getEventId(),
                "status", booking.getStatus().name(),
                "confirmedAt", booking.getConfirmedAt() == null ? "" : booking.getConfirmedAt().toString()
        );

        rabbitTemplate.convertAndSend(
                "booking.exchange",
                "booking.placed",
                payload
        );
    }
}