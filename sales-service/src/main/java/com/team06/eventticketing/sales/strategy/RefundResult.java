package com.team06.eventticketing.sales.strategy;

public class RefundResult {

    private final Double amount;
    private final String reasonCode;
    private final String strategyName;
    private final long hoursUntilEvent;

    public RefundResult(Double amount, String reasonCode, String strategyName) {
        this(amount, reasonCode, strategyName, 0L);
    }

    public RefundResult(Double amount, String reasonCode, String strategyName, long hoursUntilEvent) {
        this.amount = amount;
        this.reasonCode = reasonCode;
        this.strategyName = strategyName;
        this.hoursUntilEvent = hoursUntilEvent;
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

    public long getHoursUntilEvent() {
        return hoursUntilEvent;
    }

    public long getEventLeadTimeHours() {
        return hoursUntilEvent;
    }
}
