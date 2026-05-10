package com.team06.eventticketing.contracts.events;

public record TicketIssuedEvent(Long ticketId, Long bookingId, String attendeeName, String ticketCode) {
}
