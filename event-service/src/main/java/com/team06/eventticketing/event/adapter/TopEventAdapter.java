package com.team06.eventticketing.event.adapter;

import com.team06.eventticketing.event.dto.TopEventDTO;
import org.springframework.stereotype.Component;

@Component
public class TopEventAdapter {

    public TopEventDTO adapt(Object[] row) {
        return TopEventDTO.builder()
                .eventId(((Number) row[0]).longValue())
                .name((String) row[1])
                .rating(row[2] == null ? 0.0 : ((Number) row[2]).doubleValue())
                .totalBookings(((Number) row[3]).longValue())
                .build();
    }
}
