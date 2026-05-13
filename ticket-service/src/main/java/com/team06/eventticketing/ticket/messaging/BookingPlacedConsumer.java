package com.team06.eventticketing.ticket.messaging;

import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingPlacedConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingPlacedConsumer.class);

    private final TicketRepository ticketRepository;

    public BookingPlacedConsumer(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @RabbitListener(queues = TicketEventConfig.TICKET_BOOKING_PLACED_QUEUE)
    @Transactional
    public void onBookingPlaced(BookingPlacedEvent event) {
        if (event == null || event.bookingId() == null || event.eventId() == null) {
            log.warn("Received invalid BookingPlacedEvent: {}", event);
            return;
        }

        // Backfill eventId on any tickets that already exist for this booking
        // (rare: tickets normally come AFTER the booking, but covers race conditions)
        int updated = ticketRepository.backfillEventIdByBookingId(event.bookingId(), event.eventId());
        if (updated > 0) {
            log.info("Backfilled eventId={} on {} ticket(s) for bookingId={}",
                    event.eventId(), updated, event.bookingId());
        }
    }
}