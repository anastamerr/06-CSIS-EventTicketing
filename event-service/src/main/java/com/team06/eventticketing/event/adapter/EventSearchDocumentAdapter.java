package com.team06.eventticketing.event.adapter;

import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.search.EventSearchDocument;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EventSearchDocumentAdapter {

    public EventSearchDocument adapt(Event event) {
        EventSearchDocument document = new EventSearchDocument();
        document.setId(String.valueOf(event.getId()));
        document.setName(event.getName());
        document.setCategory(event.getCategory() == null ? null : event.getCategory().name());
        document.setVenue(event.getVenue());
        document.setDescription(resolveDescription(event.getDetails()));
        document.setEventDate(event.getEventDate());
        document.setRating(event.getRating());
        document.setStatus(event.getStatus() == null ? null : event.getStatus().name());
        return document;
    }

    private String resolveDescription(Map<String, Object> details) {
        Object description = details == null ? null : details.get("description");
        return description == null ? "" : description.toString();
    }
}
