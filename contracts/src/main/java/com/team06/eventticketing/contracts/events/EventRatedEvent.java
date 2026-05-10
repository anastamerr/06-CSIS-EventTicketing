package com.team06.eventticketing.contracts.events;

public record EventRatedEvent(Long eventId, Long bookingId, Double rating, Long userId) {
}
