package com.team06.eventticketing.sales.dto;

import com.team06.eventticketing.sales.model.TicketSaleMethod;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import java.time.LocalDateTime;
import java.util.Map;

public class TicketSaleResponse {

    private Long id;
    private Long bookingId;
    private Long userId;
    private Double amount;
    private TicketSaleMethod method;
    private TicketSaleStatus status;
    private Map<String, Object> transactionDetails;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long id;
        private Long bookingId;
        private Long userId;
        private Double amount;
        private TicketSaleMethod method;
        private TicketSaleStatus status;
        private Map<String, Object> transactionDetails;
        private LocalDateTime createdAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder bookingId(Long bookingId) {
            this.bookingId = bookingId;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder amount(Double amount) {
            this.amount = amount;
            return this;
        }

        public Builder method(TicketSaleMethod method) {
            this.method = method;
            return this;
        }

        public Builder status(TicketSaleStatus status) {
            this.status = status;
            return this;
        }

        public Builder transactionDetails(Map<String, Object> transactionDetails) {
            this.transactionDetails = transactionDetails;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TicketSaleResponse build() {
            TicketSaleResponse response = new TicketSaleResponse();
            response.setId(id);
            response.setBookingId(bookingId);
            response.setUserId(userId);
            response.setAmount(amount);
            response.setMethod(method);
            response.setStatus(status);
            response.setTransactionDetails(transactionDetails);
            response.setCreatedAt(createdAt);
            return response;
        }
    }
}
