package com.team06.eventticketing.contracts.dto;

public record BookingDTO(Long id, Long userId, Long eventId, String status, Double totalAmount) {
}
