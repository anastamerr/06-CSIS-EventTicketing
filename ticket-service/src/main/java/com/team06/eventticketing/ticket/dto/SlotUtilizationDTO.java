package com.team06.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public class SlotUtilizationDTO {

    private final long eventId;
    private final long totalTickets;
    private final long usedTickets;
    private final long validTickets;
    private final double attendanceRate;
    private final LocalDateTime lastCheckIn;

    public SlotUtilizationDTO(
            long eventId,
            long totalTickets,
            long usedTickets,
            long validTickets,
            double attendanceRate,
            LocalDateTime lastCheckIn) {
        this.eventId = eventId;
        this.totalTickets = totalTickets;
        this.usedTickets = usedTickets;
        this.validTickets = validTickets;
        this.attendanceRate = attendanceRate;
        this.lastCheckIn = lastCheckIn;
    }

    public long getEventId() {
        return eventId;
    }

    public long getTotalTickets() {
        return totalTickets;
    }

    public long getUsedTickets() {
        return usedTickets;
    }

    public long getValidTickets() {
        return validTickets;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public LocalDateTime getLastCheckIn() {
        return lastCheckIn;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long eventId;
        private long totalTickets;
        private long usedTickets;
        private long validTickets;
        private double attendanceRate;
        private LocalDateTime lastCheckIn;

        public Builder eventId(long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder totalTickets(long totalTickets) {
            this.totalTickets = totalTickets;
            return this;
        }

        public Builder usedTickets(long usedTickets) {
            this.usedTickets = usedTickets;
            return this;
        }

        public Builder validTickets(long validTickets) {
            this.validTickets = validTickets;
            return this;
        }

        public Builder attendanceRate(double attendanceRate) {
            this.attendanceRate = attendanceRate;
            return this;
        }

        public Builder lastCheckIn(LocalDateTime lastCheckIn) {
            this.lastCheckIn = lastCheckIn;
            return this;
        }

        public SlotUtilizationDTO build() {
            return new SlotUtilizationDTO(eventId, totalTickets, usedTickets, validTickets, attendanceRate, lastCheckIn);
        }
    }
}
