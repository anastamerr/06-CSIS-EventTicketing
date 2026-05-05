package com.team06.eventticketing.sales.strategy;

import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.model.TicketSale;
import java.time.LocalDateTime;

public class NoRefundStrategy implements RefundStrategy {

    private final long hoursUntilEvent;

    public NoRefundStrategy() {
        this(0L);
    }

    public NoRefundStrategy(long hoursUntilEvent) {
        this.hoursUntilEvent = hoursUntilEvent;
    }

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate) {
        return new RefundResult(0.0, "refund window expired", getClass().getSimpleName(), hoursUntilEvent);
    }
}
