package com.team06.eventticketing.contracts.events;

public record PaymentFailedEvent(Long saleId, Long bookingId, String reason) {
}
