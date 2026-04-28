package com.team06.eventticketing.sales.adapter;

import com.team06.eventticketing.sales.dto.AuditEventDTO;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    private static final java.util.Set<String> PAYMENT_SHAPED_ACTIONS = java.util.Set.of(
            "CREATED",
            "COMPLETED",
            "FAILED",
            "REFUNDED",
            "REFUND_DENIED"
    );

    public AuditEventDTO adapt(Document document) {
        AuditEventDTO dto = new AuditEventDTO();
        if (document == null) {
            return dto;
        }

        String action = document.getString("action");
        dto.setAction(action);
        dto.setTimestamp(toLocalDateTime(document.get("timestamp")));
        if (PAYMENT_SHAPED_ACTIONS.contains(action)) {
            dto.setMethod(document.getString("method"));
            dto.setAmount(toDouble(document.get("amount")));
        }

        Object details = document.get("details");
        dto.setDetails(details instanceof Map<?, ?> map ? copyMap(map) : new LinkedHashMap<>());
        return dto;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof String string && !string.isBlank()) {
            return LocalDateTime.parse(string);
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Double.parseDouble(string);
        }
        return null;
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(String.valueOf(key), value));
        return target;
    }
}
