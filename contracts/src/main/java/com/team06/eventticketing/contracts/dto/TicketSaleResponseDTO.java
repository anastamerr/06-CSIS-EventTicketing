package com.team06.eventticketing.contracts.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record TicketSaleResponseDTO(
        Long id,
        Long bookingId,
        Long userId,
        Double amount,
        String method,
        String status,
        Map<String, Object> transactionDetails,
        LocalDateTime createdAt
) {
}
