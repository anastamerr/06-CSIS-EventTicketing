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
}
