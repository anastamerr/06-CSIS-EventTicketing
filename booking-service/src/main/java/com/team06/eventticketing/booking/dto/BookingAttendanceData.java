package com.team06.eventticketing.booking.dto;

public record BookingAttendanceData(
        Long bookingId,
        String bookingStatus,
        Long userId,
        String userName,
        Long eventId,
        String eventName,
        String eventCategory
) {
}
