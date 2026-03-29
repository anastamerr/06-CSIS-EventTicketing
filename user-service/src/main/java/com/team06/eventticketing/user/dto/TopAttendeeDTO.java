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
}
