package com.team06.eventticketing.sales.strategy;

public class RefundResult {

    private final Double amount;
    private final String reasonCode;
    private final String strategyName;

    public RefundResult(Double amount, String reasonCode, String strategyName) {
        this.amount = amount;
        this.reasonCode = reasonCode;
        this.strategyName = strategyName;
    }

    public Double getAmount() {
        return amount;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getStrategyName() {
        return strategyName;
    }
}