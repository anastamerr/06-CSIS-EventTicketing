package com.team06.eventticketing.booking.dto;

public record BookingCostEstimateDTO(
    Double ticketCost,
    Double serviceFee,
    Double estimatedTotal,
    Double demandMultiplier
) {
}
