package com.team06.eventticketing.booking.dto;

public class BookingAnalyticsDTO {

    private final long totalBookings;
    private final long completedBookings;
    private final long cancelledBookings;
    private final double totalRevenue;
    private final double averageBookingAmount;
    private final double completionRate;

    public BookingAnalyticsDTO(
            long totalBookings,
            long completedBookings,
            long cancelledBookings,
            double totalRevenue,
            double averageBookingAmount,
            double completionRate
    ) {
        this.totalBookings = totalBookings;
        this.completedBookings = completedBookings;
        this.cancelledBookings = cancelledBookings;
        this.totalRevenue = totalRevenue;
        this.averageBookingAmount = averageBookingAmount;
        this.completionRate = completionRate;
    }

    public long getTotalBookings() {
        return totalBookings;
    }

    public long totalBookings() {
        return totalBookings;
    }

    public long getCompletedBookings() {
        return completedBookings;
    }

    public long completedBookings() {
        return completedBookings;
    }

    public long getCancelledBookings() {
        return cancelledBookings;
    }

    public long cancelledBookings() {
        return cancelledBookings;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public double totalRevenue() {
        return totalRevenue;
    }

    public double getAverageBookingAmount() {
        return averageBookingAmount;
    }

    public double averageBookingAmount() {
        return averageBookingAmount;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public double completionRate() {
        return completionRate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long totalBookings;
        private long completedBookings;
        private long cancelledBookings;
        private double totalRevenue;
        private double averageBookingAmount;
        private double completionRate;

        public Builder totalBookings(long totalBookings) {
            this.totalBookings = totalBookings;
            return this;
        }

        public Builder completedBookings(long completedBookings) {
            this.completedBookings = completedBookings;
            return this;
        }

        public Builder cancelledBookings(long cancelledBookings) {
            this.cancelledBookings = cancelledBookings;
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

        public Builder completionRate(double completionRate) {
            this.completionRate = completionRate;
            return this;
        }

        public BookingAnalyticsDTO build() {
            return new BookingAnalyticsDTO(
                    totalBookings,
                    completedBookings,
                    cancelledBookings,
                    totalRevenue,
                    averageBookingAmount,
                    completionRate);
        }
    }
}
