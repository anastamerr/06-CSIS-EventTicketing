package com.team06.eventticketing.sales.adapter;

import com.team06.eventticketing.sales.dto.TierRevenueDTO;
import org.springframework.stereotype.Component;

@Component
public class TierRevenueRowAdapter {

    private static final String UNSPECIFIED_TIER = "UNSPECIFIED";

    public TierRevenueDTO adapt(Object[] row) {
        String tier = resolveTier(row[0]);
        double totalRevenue = toDouble(row[1]);
        long saleCount = toLong(row[2]);
        long ticketsSold = toLong(row[3]);
        double averageRevenuePerSale = saleCount == 0 ? 0.0 : totalRevenue / saleCount;

        return TierRevenueDTO.builder()
                .tier(tier)
                .totalRevenue(totalRevenue)
                .saleCount(saleCount)
                .ticketsSold(ticketsSold)
                .averageRevenuePerSale(averageRevenuePerSale)
                .build();
    }

    private String resolveTier(Object value) {
        if (value == null) {
            return UNSPECIFIED_TIER;
        }
        String tier = value.toString().trim();
        return tier.isEmpty() ? UNSPECIFIED_TIER : tier;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Double.parseDouble(string);
        }
        return 0.0;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return 0L;
    }
}
