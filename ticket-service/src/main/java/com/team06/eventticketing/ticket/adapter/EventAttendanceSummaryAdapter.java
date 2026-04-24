package com.team06.eventticketing.ticket.adapter;

import com.team06.eventticketing.ticket.dto.EventAttendanceSummaryDTO;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class EventAttendanceSummaryAdapter {

    public EventAttendanceSummaryDTO adapt(long eventId, Object[] row) {
        long totalTickets = toLong(row[0]);
        long usedTickets = toLong(row[1]);
        long validTickets = toLong(row[2]);
        double attendanceRate = totalTickets == 0 ? 0.0 : (usedTickets * 100.0) / totalTickets;
        return EventAttendanceSummaryDTO.builder()
                .eventId(eventId)
                .totalTickets(totalTickets)
                .usedTickets(usedTickets)
                .validTickets(validTickets)
                .attendanceRate(attendanceRate)
                .lastCheckIn(toLocalDateTime(row[3]))
                .build();
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString());
    }
}
