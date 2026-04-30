package com.team06.eventticketing.event.adapter;

import com.team06.eventticketing.event.dto.EventDashboardDTO;
import com.team06.eventticketing.event.model.Event;
import org.springframework.stereotype.Component;

@Component
public class EventDashboardAdapter {

    public EventDashboardDTO adapt(Event event, Object[] row) {
        long totalBookings = toLong(row[0]);
        double totalRevenue = toDouble(row[1]);
        long totalTicketsSold = toLong(row[2]);
        long usedTickets = toLong(row[3]);
        double averageAttendanceRate = totalTicketsSold == 0 ? 0.0 : (double) usedTickets / totalTicketsSold;
        double averageRating = event.getRating() == null ? 0.0 : event.getRating();

        return EventDashboardDTO.builder()
                .eventId(event.getId())
                .name(event.getName())
                .totalBookings(totalBookings)
                .totalTicketsSold(totalTicketsSold)
                .totalRevenue(totalRevenue)
                .averageAttendanceRate(averageAttendanceRate)
                .averageRating(averageRating)
                .build();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private double toDouble(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }
}
