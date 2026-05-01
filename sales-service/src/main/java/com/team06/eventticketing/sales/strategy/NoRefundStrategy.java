package com.team06.eventticketing.sales.strategy;

import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.model.TicketSale;
import java.time.LocalDateTime;

public class NoRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate) {
        return new RefundResult(0.0, "refund window expired", getClass().getSimpleName());
    }
}