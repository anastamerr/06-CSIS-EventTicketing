package com.team06.eventticketing.sales.adapter;

import com.team06.eventticketing.sales.dto.PromotionUsageDTO;
import com.team06.eventticketing.sales.model.PromotionDiscountType;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class PromotionUsageAdapter implements ObjectArrayDtoAdapter<PromotionUsageDTO> {

    @Override
    public PromotionUsageDTO adapt(Object[] source) {
        LocalDateTime expiryDate = (LocalDateTime) source[7];
        boolean expired = expiryDate != null && expiryDate.isBefore(LocalDateTime.now());

        return PromotionUsageDTO.builder()
                .promotionId((Long) source[0])
                .code((String) source[1])
                .discountType((PromotionDiscountType) source[2])
                .discountValue(numberValue(source[3]))
                .timesUsed(integerValue(source[4]))
                .totalDiscountGiven(numberValue(source[5]))
                .active((Boolean) source[6])
                .expired(expired)
                .build();
    }

    private Double numberValue(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private Integer integerValue(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
