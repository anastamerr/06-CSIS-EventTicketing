package com.team06.eventticketing.user.adapter;

import com.team06.eventticketing.user.dto.TopAttendeeDTO;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.stereotype.Component;

@Component
public class TopAttendeeAdapter implements ObjectArrayDtoAdapter<TopAttendeeDTO> {

    @Override
    public TopAttendeeDTO adapt(Object[] source) {
        Object[] row = unwrap(source);
        return TopAttendeeDTO.builder()
                .userId(toLong(row[0]))
                .name(row[1] == null ? null : row[1].toString())
                .totalSpent(toBigDecimal(row[2]))
                .bookingCount(toLong(row[3]))
                .build();
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
