package com.team06.eventticketing.event.adapter;

import com.team06.eventticketing.event.dto.EventSessionAlertDTO;
import com.team06.eventticketing.event.model.EventStatus;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public EventSessionAlertDTO adapt(Document document) {
        EventSessionAlertDTO dto = new EventSessionAlertDTO();
        if (document == null) {
            return dto;
        }
        dto.setEventId(asLong(document.get("eventId")));
        dto.setEventName(document.getString("eventName"));
        dto.setUnverifiedCount(asInt(document.get("unverifiedCount")));
        Object status = document.get("eventStatus");
        if (status != null) {
            dto.setEventStatus(EventStatus.valueOf(status.toString()));
        }
        return dto;
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
