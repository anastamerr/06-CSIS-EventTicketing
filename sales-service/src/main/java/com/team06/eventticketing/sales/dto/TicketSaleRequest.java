package com.team06.eventticketing.sales.dto;

import com.team06.eventticketing.sales.model.TicketSaleMethod;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public class TicketSaleRequest {

    private Long bookingId;
    private Long userId;
    private Double amount;
    private TicketSaleMethod method;
    private TicketSaleStatus status;
    private Map<String, Object> transactionDetails = new LinkedHashMap<>();

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public TicketSaleMethod getMethod() {
        return method;
    }

    public void setMethod(TicketSaleMethod method) {
        this.method = method;
    }

    public TicketSaleStatus getStatus() {
        return status;
    }

    public void setStatus(TicketSaleStatus status) {
        this.status = status;
    }

    public Map<String, Object> getTransactionDetails() {
        return transactionDetails;
    }

    public void setTransactionDetails(Map<String, Object> transactionDetails) {
        this.transactionDetails = transactionDetails;
    }
}
