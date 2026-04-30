package com.team06.eventticketing.user.adapter;

import com.team06.eventticketing.common.observer.AuthEvent;
import com.team06.eventticketing.user.dto.UserActivityEventDTO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public Map<String, Object> adapt(Document document) {
        return document == null ? Map.of() : new LinkedHashMap<>(document);
    }

    public UserActivityEventDTO adapt(AuthEvent event) {
        return new UserActivityEventDTO(
                event.getAction(),
                event.getTimestamp(),
                event.getDetails() == null ? Map.of() : event.getDetails());
    }

    public UserActivityEventDTO adaptActivity(Document document) {
        return new UserActivityEventDTO(
                document.getString("action"),
                extractTimestamp(document.get("timestamp")),
                extractDetails(document.get("details")));
    }

    private LocalDateTime extractTimestamp(Object value) {
        if (value instanceof LocalDateTime timestamp) {
            return timestamp;
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (value instanceof String text) {
            return LocalDateTime.parse(text);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDetails(Object value) {
        if (value instanceof Document document) {
            return new LinkedHashMap<>(document);
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }
}
