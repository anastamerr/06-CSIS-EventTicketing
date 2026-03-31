package com.team06.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public record EventAttendanceSummaryDTO(
        long eventId,
        long totalTickets,
        long usedTickets,
        long validTickets,
        double attendanceRate,
        LocalDateTime lastCheckIn
) {}