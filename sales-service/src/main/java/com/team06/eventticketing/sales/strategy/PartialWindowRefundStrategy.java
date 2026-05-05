package com.team06.eventticketing.sales.strategy;

import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.model.TicketSale;
import java.time.LocalDateTime;

public class PartialWindowRefundStrategy implements RefundStrategy {

    private final long hoursUntilEvent;

    public PartialWindowRefundStrategy() {
        this(0L);
    }

    public PartialWindowRefundStrategy(long hoursUntilEvent) {
        this.hoursUntilEvent = hoursUntilEvent;
    }

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate) {
        double amount = sale.getAmount() == null ? 0.0 : sale.getAmount() * 0.5;
        return new RefundResult(amount, "partial refund", getClass().getSimpleName(), hoursUntilEvent);
    }
}
