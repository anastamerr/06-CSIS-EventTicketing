package com.team06.eventticketing.booking.dto;

public record AttendanceResponse(
        String message,
        Long bookingId,
        Long userId,
        Long eventId,
        boolean alreadyRecorded
) {
}
