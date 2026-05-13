package com.team06.eventticketing.contracts.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record EventDTO(
        Long id,
        String name,
        String venue,
        LocalDateTime eventDate,
        String category,
        String status,
        Double rating,
        Map<String, Object> details
) {
}
