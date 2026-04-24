package com.team06.eventticketing.user.adapter;

import com.team06.eventticketing.user.dto.UserBookingSummaryDTO;
import com.team06.eventticketing.user.model.User;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.stereotype.Component;

@Component
public class UserBookingSummaryAdapter {

    public UserBookingSummaryDTO adapt(User user, Object[] source) {
        Object[] row = unwrap(source);
        return UserBookingSummaryDTO.builder()
                .userId(valueAt(row, 0) == null ? user.getId() : toLong(valueAt(row, 0)))
                .name(valueAt(row, 1) == null ? user.getName() : valueAt(row, 1).toString())
                .totalBookings(toLong(valueAt(row, 2)))
                .completedBookings(toLong(valueAt(row, 3)))
                .cancelledBookings(toLong(valueAt(row, 4)))
                .totalSpent(toBigDecimal(valueAt(row, 5)))
                .averageBookingAmount(toBigDecimal(valueAt(row, 6)))
                .build();
    }

    public UserBookingSummaryDTO empty(User user) {
        return UserBookingSummaryDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .totalBookings(0L)
                .completedBookings(0L)
                .cancelledBookings(0L)
                .totalSpent(BigDecimal.ZERO)
                .averageBookingAmount(BigDecimal.ZERO)
                .build();
    }

    private Object valueAt(Object[] row, int index) {
        return row == null || index >= row.length ? null : row[index];
    }

    private Object[] unwrap(Object[] row) {
        Object[] current = row;
        while (current != null && current.length == 1 && current[0] instanceof Object[] nested) {
            current = nested;
        }
        return current;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }
}
