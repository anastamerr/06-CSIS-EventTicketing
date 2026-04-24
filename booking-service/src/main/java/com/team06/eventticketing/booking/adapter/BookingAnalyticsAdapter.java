package com.team06.eventticketing.booking.adapter;

import com.team06.eventticketing.booking.dto.BookingAnalyticsDTO;
import org.springframework.stereotype.Component;

@Component
public class BookingAnalyticsAdapter {

    public BookingAnalyticsDTO adapt(Object[] row) {
        long totalBookings = toLong(row[0]);
        long completedBookings = toLong(row[1]);
        long cancelledBookings = toLong(row[2]);
        double totalRevenue = toDouble(row[3]);
        double averageBookingAmount = toDouble(row[4]);
        double completionRate = totalBookings == 0 ? 0.0 : (completedBookings * 100.0) / totalBookings;

        return BookingAnalyticsDTO.builder()
                .totalBookings(totalBookings)
                .completedBookings(completedBookings)
                .cancelledBookings(cancelledBookings)
                .totalRevenue(totalRevenue)
                .averageBookingAmount(averageBookingAmount)
                .completionRate(completionRate)
                .build();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private double toDouble(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }
}
