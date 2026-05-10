package com.team06.eventticketing.contracts.events;

import java.math.BigDecimal;

public record PaymentRefundedEvent(Long saleId, Long bookingId, BigDecimal refundAmount) {
}
