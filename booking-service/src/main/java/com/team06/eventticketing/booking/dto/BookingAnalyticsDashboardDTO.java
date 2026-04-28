package com.team06.eventticketing.booking.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BookingAnalyticsDashboardDTO {

    private final long totalBookings;
    private final double totalRevenue;
    private final double averageBookingValue;
    private final double conversionRate;
    private final Map<String, Long> bookingsByStatus;

    @JsonCreator
    public BookingAnalyticsDashboardDTO(
            @JsonProperty("totalBookings")
            long totalBookings,
            @JsonProperty("totalRevenue")
            double totalRevenue,
            @JsonProperty("averageBookingValue")
            double averageBookingValue,
            @JsonProperty("conversionRate")
            double conversionRate,
            @JsonProperty("bookingsByStatus")
            Map<String, Long> bookingsByStatus
    ) {
        this.totalBookings = totalBookings;
        this.totalRevenue = totalRevenue;
        this.averageBookingValue = averageBookingValue;
        this.conversionRate = conversionRate;
        this.bookingsByStatus = Collections.unmodifiableMap(new LinkedHashMap<>(
                bookingsByStatus == null ? new LinkedHashMap<>() : bookingsByStatus));
    }

    public long getTotalBookings() {
        return totalBookings;
    }

    public long totalBookings() {
        return totalBookings;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public double totalRevenue() {
        return totalRevenue;
    }

    public double getAverageBookingValue() {
        return averageBookingValue;
    }

    public double averageBookingValue() {
        return averageBookingValue;
    }

    public double getConversionRate() {
        return conversionRate;
    }

    public double conversionRate() {
        return conversionRate;
    }

    public Map<String, Long> getBookingsByStatus() {
        return bookingsByStatus;
    }

    public Map<String, Long> bookingsByStatus() {
        return bookingsByStatus;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long totalBookings;
        private double totalRevenue;
        private double averageBookingValue;
        private double conversionRate;
        private Map<String, Long> bookingsByStatus = new LinkedHashMap<>();

        public Builder totalBookings(long totalBookings) {
            this.totalBookings = totalBookings;
            return this;
        }

        public Builder totalRevenue(double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder averageBookingValue(double averageBookingValue) {
            this.averageBookingValue = averageBookingValue;
            return this;
        }

        public Builder conversionRate(double conversionRate) {
            this.conversionRate = conversionRate;
            return this;
        }

        public Builder bookingsByStatus(Map<String, Long> bookingsByStatus) {
            this.bookingsByStatus = bookingsByStatus == null ? new LinkedHashMap<>() : bookingsByStatus;
            return this;
        }

        public BookingAnalyticsDashboardDTO build() {
            return new BookingAnalyticsDashboardDTO(
                    totalBookings,
                    totalRevenue,
                    averageBookingValue,
                    conversionRate,
                    bookingsByStatus);
        }
    }
}
