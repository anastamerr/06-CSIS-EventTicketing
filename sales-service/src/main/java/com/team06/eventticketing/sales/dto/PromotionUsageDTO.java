package com.team06.eventticketing.sales.dto;

import com.team06.eventticketing.sales.model.PromotionDiscountType;

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
}
