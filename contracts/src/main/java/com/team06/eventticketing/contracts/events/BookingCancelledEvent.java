package com.team06.eventticketing.contracts.events;

public record BookingCancelledEvent(Long bookingId, Long userId, Long eventId, String reason) {
}
