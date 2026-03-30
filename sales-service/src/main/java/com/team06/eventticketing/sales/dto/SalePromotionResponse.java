package com.team06.eventticketing.sales.dto;

import java.time.LocalDateTime;

public class SalePromotionResponse {

    private Long id;
    private Long ticketSaleId;
    private Long promotionId;
    private Double discountApplied;
    private LocalDateTime appliedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTicketSaleId() {
        return ticketSaleId;
    }

    public void setTicketSaleId(Long ticketSaleId) {
        this.ticketSaleId = ticketSaleId;
    }

    public Long getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(Long promotionId) {
        this.promotionId = promotionId;
    }

    public Double getDiscountApplied() {
        return discountApplied;
    }

    public void setDiscountApplied(Double discountApplied) {
        this.discountApplied = discountApplied;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}
