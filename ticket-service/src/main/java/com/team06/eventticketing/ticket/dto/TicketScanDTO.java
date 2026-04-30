package com.team06.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public record TicketScanDTO(
        LocalDateTime timestamp,
        String scanType,
        String attendeeName,
        String gate,
        String section,
        String seatNumber,
        String notes
) {
}
