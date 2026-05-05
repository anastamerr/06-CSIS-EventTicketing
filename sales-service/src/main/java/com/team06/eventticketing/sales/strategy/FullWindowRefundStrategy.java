package com.team06.eventticketing.sales.strategy;

import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.model.TicketSale;
import java.time.LocalDateTime;

public class FullWindowRefundStrategy implements RefundStrategy {

    private final long hoursUntilEvent;

    public FullWindowRefundStrategy() {
        this(0L);
    }

    public FullWindowRefundStrategy(long hoursUntilEvent) {
        this.hoursUntilEvent = hoursUntilEvent;
    }

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate) {
        double amount = sale.getAmount() == null ? 0.0 : sale.getAmount();
        return new RefundResult(amount, "full refund", getClass().getSimpleName(), hoursUntilEvent);
    }
}
