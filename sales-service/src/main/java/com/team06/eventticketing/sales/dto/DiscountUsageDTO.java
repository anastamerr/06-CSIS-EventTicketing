package com.team06.eventticketing.sales.dto;

import com.team06.eventticketing.sales.model.PromotionDiscountType;

public class DiscountUsageDTO {

    private final Long promotionId;
    private final String code;
    private final PromotionDiscountType discountType;
    private final Double discountValue;
    private final Integer timesUsed;
    private final Double totalDiscountGiven;
    private final Boolean active;
    private final Boolean expired;

    public DiscountUsageDTO(
            Long promotionId,
            String code,
            PromotionDiscountType discountType,
            Double discountValue,
            Integer timesUsed,
            Double totalDiscountGiven,
            Boolean active,
            Boolean expired) {
        this.promotionId = promotionId;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.timesUsed = timesUsed;
        this.totalDiscountGiven = totalDiscountGiven;
        this.active = active;
        this.expired = expired;
    }

    public Long getPromotionId() {
        return promotionId;
    }

    public Long getDiscountId() {
        return promotionId;
    }

    public String getCode() {
        return code;
    }

    public PromotionDiscountType getDiscountType() {
        return discountType;
    }

    public Double getDiscountValue() {
        return discountValue;
    }

    public Integer getTimesUsed() {
        return timesUsed;
    }

    public Double getTotalDiscountGiven() {
        return totalDiscountGiven;
    }

    public Boolean getActive() {
        return active;
    }

    public Boolean getExpired() {
        return expired;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long promotionId;
        private String code;
        private PromotionDiscountType discountType;
        private Double discountValue;
        private Integer timesUsed;
        private Double totalDiscountGiven;
        private Boolean active;
        private Boolean expired;

        public Builder promotionId(Long promotionId) {
            this.promotionId = promotionId;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder discountType(PromotionDiscountType discountType) {
            this.discountType = discountType;
            return this;
        }

        public Builder discountValue(Double discountValue) {
            this.discountValue = discountValue;
            return this;
        }

        public Builder timesUsed(Integer timesUsed) {
            this.timesUsed = timesUsed;
            return this;
        }

        public Builder totalDiscountGiven(Double totalDiscountGiven) {
            this.totalDiscountGiven = totalDiscountGiven;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public Builder expired(Boolean expired) {
            this.expired = expired;
            return this;
        }

        public DiscountUsageDTO build() {
            return new DiscountUsageDTO(
                    promotionId,
                    code,
                    discountType,
                    discountValue,
                    timesUsed,
                    totalDiscountGiven,
                    active,
                    expired);
        }
    }
}
