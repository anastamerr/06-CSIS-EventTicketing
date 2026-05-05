package com.team06.eventticketing.sales.dto;

import com.team06.eventticketing.sales.model.PromotionDiscountType;
import com.team06.eventticketing.sales.model.TicketSaleMethod;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InvoiceDetailsDTO {

    private Long saleId;
    private Long bookingId;
    private Long userId;
    private Double originalAmount;
    private TicketSaleMethod method;
    private TicketSaleStatus status;
    private Map<String, Object> transactionDetails = new LinkedHashMap<>();
    private List<AppliedPromotionDTO> appliedPromotions = new ArrayList<>();
    private Double totalDiscount;
    private Double finalAmount;

    public Long getSaleId() {
        return saleId;
    }

    public void setSaleId(Long saleId) {
        this.saleId = saleId;
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

    public Double getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(Double originalAmount) {
        this.originalAmount = originalAmount;
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
        this.transactionDetails = transactionDetails == null ? new LinkedHashMap<>() : transactionDetails;
    }

    public List<AppliedPromotionDTO> getAppliedPromotions() {
        return appliedPromotions;
    }

    public List<AppliedPromotionDTO> getAppliedDiscounts() {
        return appliedPromotions;
    }

    public void setAppliedPromotions(List<AppliedPromotionDTO> appliedPromotions) {
        this.appliedPromotions = appliedPromotions == null ? new ArrayList<>() : appliedPromotions;
    }

    public Double getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(Double totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public Double getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(Double finalAmount) {
        this.finalAmount = finalAmount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class AppliedPromotionDTO {
        private String promotionCode;
        private PromotionDiscountType discountType;
        private Double discountApplied;
        private LocalDateTime appliedAt;

        public String getPromotionCode() {
            return promotionCode;
        }

        public void setPromotionCode(String promotionCode) {
            this.promotionCode = promotionCode;
        }

        public PromotionDiscountType getDiscountType() {
            return discountType;
        }

        public void setDiscountType(PromotionDiscountType discountType) {
            this.discountType = discountType;
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

    public static final class Builder {
        private Long saleId;
        private Long bookingId;
        private Long userId;
        private Double originalAmount;
        private TicketSaleMethod method;
        private TicketSaleStatus status;
        private Map<String, Object> transactionDetails = new LinkedHashMap<>();
        private List<AppliedPromotionDTO> appliedPromotions = new ArrayList<>();
        private Double totalDiscount;
        private Double finalAmount;

        public Builder saleId(Long saleId) {
            this.saleId = saleId;
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

        public Builder originalAmount(Double originalAmount) {
            this.originalAmount = originalAmount;
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

        public Builder appliedPromotions(List<AppliedPromotionDTO> appliedPromotions) {
            this.appliedPromotions = appliedPromotions;
            return this;
        }

        public Builder totalDiscount(Double totalDiscount) {
            this.totalDiscount = totalDiscount;
            return this;
        }

        public Builder finalAmount(Double finalAmount) {
            this.finalAmount = finalAmount;
            return this;
        }

        public InvoiceDetailsDTO build() {
            InvoiceDetailsDTO dto = new InvoiceDetailsDTO();
            dto.setSaleId(saleId);
            dto.setBookingId(bookingId);
            dto.setUserId(userId);
            dto.setOriginalAmount(originalAmount);
            dto.setMethod(method);
            dto.setStatus(status);
            dto.setTransactionDetails(transactionDetails);
            dto.setAppliedPromotions(appliedPromotions);
            dto.setTotalDiscount(totalDiscount);
            dto.setFinalAmount(finalAmount);
            return dto;
        }
    }
}
