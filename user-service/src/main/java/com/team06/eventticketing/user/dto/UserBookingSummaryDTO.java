package com.team06.eventticketing.user.dto;

import java.math.BigDecimal;
import java.util.Objects;

public class UserBookingSummaryDTO {

    private final Long userId;
    private final String name;
    private final Long totalBookings;
    private final Long completedBookings;
    private final Long cancelledBookings;
    private final BigDecimal totalSpent;
    private final BigDecimal averageBookingAmount;

    public UserBookingSummaryDTO(Long userId,
                                 String name,
                                 Long totalBookings,
                                 Long completedBookings,
                                 Long cancelledBookings,
                                 BigDecimal totalSpent,
                                 BigDecimal averageBookingAmount) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.name = name;
        this.totalBookings = totalBookings == null ? 0L : totalBookings;
        this.completedBookings = completedBookings == null ? 0L : completedBookings;
        this.cancelledBookings = cancelledBookings == null ? 0L : cancelledBookings;
        this.totalSpent = totalSpent == null ? BigDecimal.ZERO : totalSpent;
        this.averageBookingAmount = averageBookingAmount == null ? BigDecimal.ZERO : averageBookingAmount;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public Long getTotalBookings() {
        return totalBookings;
    }

    public Long getCompletedBookings() {
        return completedBookings;
    }

    public Long getCancelledBookings() {
        return cancelledBookings;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public BigDecimal getAverageBookingAmount() {
        return averageBookingAmount;
    }
}
