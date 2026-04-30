package com.team06.eventticketing.event.dto;

public class EventDashboardDTO {

    private Long eventId;
    private String name;
    private long totalBookings;
    private long totalTicketsSold;
    private double totalRevenue;
    private double averageAttendanceRate;
    private double averageRating;

    public EventDashboardDTO() {
    }

    public EventDashboardDTO(
            Long eventId,
            String name,
            long totalBookings,
            long totalTicketsSold,
            double totalRevenue,
            double averageAttendanceRate,
            double averageRating
    ) {
        this.eventId = eventId;
        this.name = name;
        this.totalBookings = totalBookings;
        this.totalTicketsSold = totalTicketsSold;
        this.totalRevenue = totalRevenue;
        this.averageAttendanceRate = averageAttendanceRate;
        this.averageRating = averageRating;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTotalBookings() {
        return totalBookings;
    }

    public void setTotalBookings(long totalBookings) {
        this.totalBookings = totalBookings;
    }

    public long getTotalTicketsSold() {
        return totalTicketsSold;
    }

    public void setTotalTicketsSold(long totalTicketsSold) {
        this.totalTicketsSold = totalTicketsSold;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public double getAverageAttendanceRate() {
        return averageAttendanceRate;
    }

    public void setAverageAttendanceRate(double averageAttendanceRate) {
        this.averageAttendanceRate = averageAttendanceRate;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long eventId;
        private String name;
        private long totalBookings;
        private long totalTicketsSold;
        private double totalRevenue;
        private double averageAttendanceRate;
        private double averageRating;

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder totalBookings(long totalBookings) {
            this.totalBookings = totalBookings;
            return this;
        }

        public Builder totalTicketsSold(long totalTicketsSold) {
            this.totalTicketsSold = totalTicketsSold;
            return this;
        }

        public Builder totalRevenue(double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder averageAttendanceRate(double averageAttendanceRate) {
            this.averageAttendanceRate = averageAttendanceRate;
            return this;
        }

        public Builder averageRating(double averageRating) {
            this.averageRating = averageRating;
            return this;
        }

        public EventDashboardDTO build() {
            return new EventDashboardDTO(
                    eventId,
                    name,
                    totalBookings,
                    totalTicketsSold,
                    totalRevenue,
                    averageAttendanceRate,
                    averageRating);
        }
    }
}
