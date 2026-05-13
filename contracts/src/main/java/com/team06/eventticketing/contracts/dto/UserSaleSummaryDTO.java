package com.team06.eventticketing.contracts.dto;

import java.util.Map;

public record UserSaleSummaryDTO(
        Long userId,
        Long totalSales,
        Double totalAmount,
        Map<String, Double> methodBreakdown,
        Map<String, Long> methodCounts
) {
}
