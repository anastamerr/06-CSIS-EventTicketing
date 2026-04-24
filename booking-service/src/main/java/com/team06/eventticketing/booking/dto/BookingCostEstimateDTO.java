package com.team06.eventticketing.booking.dto;

public class BookingCostEstimateDTO {

    private final Double ticketCost;
    private final Double serviceFee;
    private final Double demandMultiplier;
    private final Double estimatedTotal;

    public BookingCostEstimateDTO(Double ticketCost, Double serviceFee, Double demandMultiplier, Double estimatedTotal) {
        this.ticketCost = ticketCost;
        this.serviceFee = serviceFee;
        this.demandMultiplier = demandMultiplier;
        this.estimatedTotal = estimatedTotal;
    }

    public Double getTicketCost() {
        return ticketCost;
    }

    public Double ticketCost() {
        return ticketCost;
    }

    public Double getServiceFee() {
        return serviceFee;
    }

    public Double serviceFee() {
        return serviceFee;
    }

    public Double getDemandMultiplier() {
        return demandMultiplier;
    }

    public Double demandMultiplier() {
        return demandMultiplier;
    }

    public Double getEstimatedTotal() {
        return estimatedTotal;
    }

    public Double estimatedTotal() {
        return estimatedTotal;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Double ticketCost;
        private Double serviceFee;
        private Double demandMultiplier;
        private Double estimatedTotal;

        public Builder ticketCost(Double ticketCost) {
            this.ticketCost = ticketCost;
            return this;
        }

        public Builder serviceFee(Double serviceFee) {
            this.serviceFee = serviceFee;
            return this;
        }

        public Builder demandMultiplier(Double demandMultiplier) {
            this.demandMultiplier = demandMultiplier;
            return this;
        }

        public Builder estimatedTotal(Double estimatedTotal) {
            this.estimatedTotal = estimatedTotal;
            return this;
        }

        public BookingCostEstimateDTO build() {
            return new BookingCostEstimateDTO(ticketCost, serviceFee, demandMultiplier, estimatedTotal);
        }
    }
}
