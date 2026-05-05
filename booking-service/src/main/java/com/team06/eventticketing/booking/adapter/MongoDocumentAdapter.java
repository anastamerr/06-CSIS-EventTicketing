package com.team06.eventticketing.booking.adapter;

import com.team06.eventticketing.booking.dto.BookingAnalyticsDTO;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public BookingAnalyticsDTO adapt(Document document) {
        if (document == null) {
            return BookingAnalyticsDTO.builder().build();
        }
        return BookingAnalyticsDTO.builder()
                .totalBookings(asLong(document.get("totalBookings")))
                .completedBookings(asLong(document.get("completedBookings")))
                .cancelledBookings(asLong(document.get("cancelledBookings")))
                .totalRevenue(asDouble(document.get("totalRevenue")))
                .averageBookingAmount(asDouble(document.get("averageBookingAmount")))
                .completionRate(asDouble(document.get("completionRate")))
                .build();
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
