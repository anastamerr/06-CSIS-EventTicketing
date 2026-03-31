package com.team06.eventticketing.booking.dto;

import com.team06.eventticketing.booking.model.BookingItemStatus;
import java.util.Map;

public record BookingDetailsItemDTO(
        Long id,
        Integer eventOrder,
        String sessionTitle,
        Integer quantity,
        Double unitPrice,
        BookingItemStatus status,
        Map<String, Object> metadata
) {
}
