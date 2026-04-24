package com.team06.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public class EventAttendanceSummaryDTO {

    private final long eventId;
    private final long totalTickets;
    private final long usedTickets;
    private final long validTickets;
    private final double attendanceRate;
    private final LocalDateTime lastCheckIn;

    public EventAttendanceSummaryDTO(
            long eventId,
            long totalTickets,
            long usedTickets,
            long validTickets,
            double attendanceRate,
            LocalDateTime lastCheckIn
    ) {
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

    public long eventId() {
        return eventId;
    }

    public long getTotalTickets() {
        return totalTickets;
    }

    public long totalTickets() {
        return totalTickets;
    }

    public long getUsedTickets() {
        return usedTickets;
    }

    public long usedTickets() {
        return usedTickets;
    }

    public long getValidTickets() {
        return validTickets;
    }

    public long validTickets() {
        return validTickets;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public double attendanceRate() {
        return attendanceRate;
    }

    public LocalDateTime getLastCheckIn() {
        return lastCheckIn;
    }

    public LocalDateTime lastCheckIn() {
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

        public EventAttendanceSummaryDTO build() {
            return new EventAttendanceSummaryDTO(
                    eventId,
                    totalTickets,
                    usedTickets,
                    validTickets,
                    attendanceRate,
                    lastCheckIn);
        }
    }
}
