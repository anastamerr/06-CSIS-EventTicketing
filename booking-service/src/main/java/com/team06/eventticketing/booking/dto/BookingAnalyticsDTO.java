package com.team06.eventticketing.booking.dto;

public record BookingAnalyticsDTO(
        long totalBookings,
        long completedBookings,
        long cancelledBookings,
        double totalRevenue,
        double averageBookingAmount,
        double completionRate
) {}