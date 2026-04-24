package com.team06.eventticketing.event.adapter;

import com.team06.eventticketing.event.dto.EventRevenueDTO;
import org.springframework.stereotype.Component;

@Component
public class EventRevenueAdapter {

    public EventRevenueDTO adapt(Object[] row) {
        return EventRevenueDTO.builder()
                .eventId(((Number) row[0]).longValue())
                .name((String) row[1])
                .totalBookings(((Number) row[2]).longValue())
                .totalRevenue(row[3] == null ? 0.0 : ((Number) row[3]).doubleValue())
                .averageBookingAmount(row[4] == null ? 0.0 : ((Number) row[4]).doubleValue())
                .build();
    }
}
