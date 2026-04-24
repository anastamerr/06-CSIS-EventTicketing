package com.team06.eventticketing.sales.dto;

public class RevenueReportDTO {

    private double totalRevenue;
    private long totalTransactions;
    private double averageSale;
    private double refundedAmount;
    private long refundCount;

    public RevenueReportDTO() {
    }

    public RevenueReportDTO(
            double totalRevenue,
            long totalTransactions,
            double averageSale,
            double refundedAmount,
            long refundCount
    ) {
        this.totalRevenue = totalRevenue;
        this.totalTransactions = totalTransactions;
        this.averageSale = averageSale;
        this.refundedAmount = refundedAmount;
        this.refundCount = refundCount;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public double getAverageSale() {
        return averageSale;
    }

    public void setAverageSale(double averageSale) {
        this.averageSale = averageSale;
    }

    public double getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(double refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public long getRefundCount() {
        return refundCount;
    }

    public void setRefundCount(long refundCount) {
        this.refundCount = refundCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double totalRevenue;
        private long totalTransactions;
        private double averageSale;
        private double refundedAmount;
        private long refundCount;

        public Builder totalRevenue(double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder totalTransactions(long totalTransactions) {
            this.totalTransactions = totalTransactions;
            return this;
        }

        public Builder averageSale(double averageSale) {
            this.averageSale = averageSale;
            return this;
        }

        public Builder refundedAmount(double refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        public Builder refundCount(long refundCount) {
            this.refundCount = refundCount;
            return this;
        }

        public RevenueReportDTO build() {
            return new RevenueReportDTO(
                    totalRevenue,
                    totalTransactions,
                    averageSale,
                    refundedAmount,
                    refundCount
            );
        }
    }
}
