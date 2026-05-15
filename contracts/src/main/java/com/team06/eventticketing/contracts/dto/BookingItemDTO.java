package com.team06.eventticketing.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingItemDTO(
        Long id,
        Integer eventOrder,
        Long sessionId,
        String sessionTitle,
        Integer quantity,
        Double unitPrice,
        String status,
        Map<String, Object> metadata
) {
}
