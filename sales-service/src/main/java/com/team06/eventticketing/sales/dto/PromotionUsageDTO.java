package com.team06.eventticketing.sales.dto;

import com.team06.eventticketing.sales.model.PromotionDiscountType;

import java.time.LocalDateTime;

public record PromotionUsageDTO(
    Long promotionId,
    String code,
    PromotionDiscountType discountType,
    Double discountValue,
    Integer timesUsed,
    Double totalDiscountGiven,
    Boolean active,
    Boolean expired
) {
    /**
     * Factory method to create PromotionUsageDTO from query results.
     * The expired field is computed based on the expiry date.
     */
    public static PromotionUsageDTO from(
        Long promotionId,
        String code,
        PromotionDiscountType discountType,
        Double discountValue,
        Integer timesUsed,
        Double totalDiscountGiven,
        Boolean active,
        LocalDateTime expiryDate
    ) {
        boolean expired = expiryDate != null && expiryDate.isBefore(LocalDateTime.now());
        return new PromotionUsageDTO(
            promotionId,
            code,
            discountType,
            discountValue,
            timesUsed,
            totalDiscountGiven != null ? totalDiscountGiven : 0.0,
            active,
            expired
        );
    }
}
