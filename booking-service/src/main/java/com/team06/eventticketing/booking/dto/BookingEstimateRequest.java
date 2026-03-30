package com.team06.eventticketing.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BookingEstimateRequest(
    @NotNull(message = "Event ID is required")
    Long eventId,

    @NotNull(message = "Ticket count is required")
    @Min(value = 1, message = "Ticket count must be at least 1")
    Integer ticketCount,

    @NotBlank(message = "Ticket tier is required")
    @Pattern(regexp = "VIP|standard", message = "Ticket tier must be either 'VIP' or 'standard'")
    String ticketTier
) {
}
