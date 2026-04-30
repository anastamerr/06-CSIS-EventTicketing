package com.team06.eventticketing.ticket.dto;

import java.util.Map;
import java.util.Objects;

public class TicketAnalyticsDTO {

    private final long totalIssued;
    private final long usedCount;
    private final long validCount;
    private final long expiredCount;
    private final long cancelledCount;
    private final double attendanceRate;
    private final Map<String, Long> ticketsByStatus;

    private TicketAnalyticsDTO(Builder builder) {
        this.totalIssued = builder.totalIssued;
        this.usedCount = builder.usedCount;
        this.validCount = builder.validCount;
        this.expiredCount = builder.expiredCount;
        this.cancelledCount = builder.cancelledCount;
        this.attendanceRate = builder.attendanceRate;
        this.ticketsByStatus = builder.ticketsByStatus;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getTotalIssued() {
        return totalIssued;
    }

    public long getUsedCount() {
        return usedCount;
    }

    public long getValidCount() {
        return validCount;
    }

    public long getExpiredCount() {
        return expiredCount;
    }

    public long getCancelledCount() {
        return cancelledCount;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public Map<String, Long> getTicketsByStatus() {
        return ticketsByStatus;
    }

    public static final class Builder {

        private long totalIssued;
        private long usedCount;
        private long validCount;
        private long expiredCount;
        private long cancelledCount;
        private double attendanceRate;
        private Map<String, Long> ticketsByStatus = Map.of();

        private Builder() {
        }

        public Builder totalIssued(long totalIssued) {
            this.totalIssued = totalIssued;
            return this;
        }

        public Builder usedCount(long usedCount) {
            this.usedCount = usedCount;
            return this;
        }

        public Builder validCount(long validCount) {
            this.validCount = validCount;
            return this;
        }

        public Builder expiredCount(long expiredCount) {
            this.expiredCount = expiredCount;
            return this;
        }

        public Builder cancelledCount(long cancelledCount) {
            this.cancelledCount = cancelledCount;
            return this;
        }

        public Builder attendanceRate(double attendanceRate) {
            this.attendanceRate = attendanceRate;
            return this;
        }

        public Builder ticketsByStatus(Map<String, Long> ticketsByStatus) {
            this.ticketsByStatus = Objects.requireNonNull(ticketsByStatus);
            return this;
        }

        public TicketAnalyticsDTO build() {
            return new TicketAnalyticsDTO(this);
        }
    }
}
