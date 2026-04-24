package com.team06.eventticketing.sales.dto;
import java.util.Map;

public class UserSaleSummaryDTO {
    private Long userId;
    private Long totalSales;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;
    public UserSaleSummaryDTO() {
    }
    public UserSaleSummaryDTO(Long userId, Long totalSales, Double totalAmount, Map<String, Double> methodBreakdown) {
        this.userId = userId;
        this.totalSales = totalSales;
        this.totalAmount = totalAmount;
        this.methodBreakdown = methodBreakdown;
    }
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public Long getTotalSales() {
        return totalSales;
    }
    public void setTotalSales(Long totalSales) {
        this.totalSales = totalSales;
    }
    public Double getTotalAmount() {
        return totalAmount;
    }
    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }
    public Map<String, Double> getMethodBreakdown() {
        return methodBreakdown;
    }
    public void setMethodBreakdown(Map<String, Double> methodBreakdown) {
        this.methodBreakdown = methodBreakdown;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long userId;
        private Long totalSales;
        private Double totalAmount;
        private Map<String, Double> methodBreakdown;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder totalSales(Long totalSales) {
            this.totalSales = totalSales;
            return this;
        }

        public Builder totalAmount(Double totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder methodBreakdown(Map<String, Double> methodBreakdown) {
            this.methodBreakdown = methodBreakdown;
            return this;
        }

        public UserSaleSummaryDTO build() {
            return new UserSaleSummaryDTO(userId, totalSales, totalAmount, methodBreakdown);
        }
    }
}
