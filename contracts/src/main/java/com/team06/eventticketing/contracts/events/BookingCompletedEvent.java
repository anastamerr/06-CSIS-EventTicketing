package com.team06.eventticketing.contracts.events;

import java.math.BigDecimal;

public record BookingCompletedEvent(Long bookingId, Long userId, Long eventId, BigDecimal totalAmount) {
}
