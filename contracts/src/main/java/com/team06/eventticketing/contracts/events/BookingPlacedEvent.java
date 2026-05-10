package com.team06.eventticketing.contracts.events;

public record BookingPlacedEvent(Long bookingId, Long userId, Long eventId) {
}
