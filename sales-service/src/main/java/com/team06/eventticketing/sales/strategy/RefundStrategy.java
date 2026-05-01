package com.team06.eventticketing.sales.strategy;

import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.model.TicketSale;
import java.time.LocalDateTime;

public interface RefundStrategy {

    RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate);
}