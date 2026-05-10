package com.team06.eventticketing.contracts.events;

public record TicketCancelledEvent(Long ticketId, Long bookingId) {
}
