package com.team06.eventticketing.sales.dto;

public class TierRevenueDTO {

    private String tier;
    private double totalRevenue;
    private long saleCount;
    private long ticketsSold;
    private double averageRevenuePerSale;

    public TierRevenueDTO() {
    }

    public TierRevenueDTO(
            String tier,
            double totalRevenue,
            long saleCount,
            long ticketsSold,
            double averageRevenuePerSale) {
        this.tier = tier;
        this.totalRevenue = totalRevenue;
        this.saleCount = saleCount;
        this.ticketsSold = ticketsSold;
        this.averageRevenuePerSale = averageRevenuePerSale;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getSaleCount() {
        return saleCount;
    }

    public void setSaleCount(long saleCount) {
        this.saleCount = saleCount;
    }

    public long getTicketsSold() {
        return ticketsSold;
    }

    public void setTicketsSold(long ticketsSold) {
        this.ticketsSold = ticketsSold;
    }

    public double getAverageRevenuePerSale() {
        return averageRevenuePerSale;
    }

    public void setAverageRevenuePerSale(double averageRevenuePerSale) {
        this.averageRevenuePerSale = averageRevenuePerSale;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tier;
        private double totalRevenue;
        private long saleCount;
        private long ticketsSold;
        private double averageRevenuePerSale;

        public Builder tier(String tier) {
            this.tier = tier;
            return this;
        }

        public Builder totalRevenue(double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder saleCount(long saleCount) {
            this.saleCount = saleCount;
            return this;
        }

        public Builder ticketsSold(long ticketsSold) {
            this.ticketsSold = ticketsSold;
            return this;
        }

        public Builder averageRevenuePerSale(double averageRevenuePerSale) {
            this.averageRevenuePerSale = averageRevenuePerSale;
            return this;
        }

        public TierRevenueDTO build() {
            return new TierRevenueDTO(tier, totalRevenue, saleCount, ticketsSold, averageRevenuePerSale);
        }
    }
}
