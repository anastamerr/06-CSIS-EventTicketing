package com.team06.eventticketing.contracts.events;

public record EventStatusChangedEvent(Long eventId, String oldStatus, String newStatus) {
}
