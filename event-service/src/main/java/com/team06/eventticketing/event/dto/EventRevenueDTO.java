package com.team06.eventticketing.event.dto;

public class EventRevenueDTO {

    private Long eventId;
    private String name;
    private long totalBookings;
    private double totalRevenue;
    private double averageBookingAmount;

    public EventRevenueDTO() {
    }

    public EventRevenueDTO(Long eventId, String name, long totalBookings, double totalRevenue, double averageBookingAmount) {
        this.eventId = eventId;
        this.name = name;
        this.totalBookings = totalBookings;
        this.totalRevenue = totalRevenue;
        this.averageBookingAmount = averageBookingAmount;
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

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public double getTotalEarnings() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public double getAverageBookingAmount() {
        return averageBookingAmount;
    }

    public void setAverageBookingAmount(double averageBookingAmount) {
        this.averageBookingAmount = averageBookingAmount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long eventId;
        private String name;
        private long totalBookings;
        private double totalRevenue;
        private double averageBookingAmount;

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

        public Builder totalRevenue(double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder averageBookingAmount(double averageBookingAmount) {
            this.averageBookingAmount = averageBookingAmount;
            return this;
        }

        public EventRevenueDTO build() {
            return new EventRevenueDTO(eventId, name, totalBookings, totalRevenue, averageBookingAmount);
        }
    }
}
