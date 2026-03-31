package com.team06.eventticketing.booking.dto;

import com.team06.eventticketing.booking.model.BookingStatus;
import java.util.List;
import java.util.Map;

public record BookingDetailsDTO(
        Long bookingId,
        Long userId,
        Long eventId,
        BookingStatus status,
        Double totalAmount,
        Map<String, Object> metadata,
        List<BookingDetailsItemDTO> items,
        int totalItems,
        int confirmedItems
) {
}
