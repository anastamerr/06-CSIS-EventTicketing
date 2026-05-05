package com.team06.eventticketing.user.dto;

import java.math.BigDecimal;
import java.util.Objects;

public class TopAttendeeDTO {

    private final Long userId;
    private final String name;
    private final BigDecimal totalSpent;
    private final Long bookingCount;

    public TopAttendeeDTO(Long userId, String name, BigDecimal totalSpent, Long bookingCount) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.name = name;
        this.totalSpent = totalSpent == null ? BigDecimal.ZERO : totalSpent;
        this.bookingCount = bookingCount == null ? 0L : bookingCount;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public Long getBookingCount() {
        return bookingCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long userId;
        private String name;
        private BigDecimal totalSpent;
        private Long bookingCount;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder totalSpent(BigDecimal totalSpent) {
            this.totalSpent = totalSpent;
            return this;
        }

        public Builder bookingCount(Long bookingCount) {
            this.bookingCount = bookingCount;
            return this;
        }

        public TopAttendeeDTO build() {
            return new TopAttendeeDTO(userId == null ? 0L : userId, name, totalSpent, bookingCount);
        }
    }
}
