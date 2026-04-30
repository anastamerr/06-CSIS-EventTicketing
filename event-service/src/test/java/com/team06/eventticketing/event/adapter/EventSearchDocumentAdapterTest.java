package com.team06.eventticketing.event.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventCategory;
import com.team06.eventticketing.event.model.EventStatus;
import com.team06.eventticketing.event.search.EventSearchDocument;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventSearchDocumentAdapterTest {

    private final EventSearchDocumentAdapter adapter = new EventSearchDocumentAdapter();

    @Test
    void adaptMapsEventFieldsToSearchDocument() {
        Event event = event("Broadway musical with live orchestra");

        EventSearchDocument document = adapter.adapt(event);

        assertEquals("10", document.getId());
        assertEquals("Broadway Night", document.getName());
        assertEquals("THEATER", document.getCategory());
        assertEquals("Main Hall", document.getVenue());
        assertEquals("Broadway musical with live orchestra", document.getDescription());
        assertEquals(LocalDateTime.of(2026, 5, 10, 20, 0), document.getEventDate());
        assertEquals(4.5, document.getRating());
        assertEquals("UPCOMING", document.getStatus());
    }

    @Test
    void adaptDefaultsMissingDescriptionToEmptyString() {
        Event event = event(null);
        event.setDetails(new LinkedHashMap<>());

        EventSearchDocument document = adapter.adapt(event);

        assertEquals("", document.getDescription());
    }

    private Event event(String description) {
        Event event = new Event();
        event.setId(10L);
        event.setName("Broadway Night");
        event.setCategory(EventCategory.THEATER);
        event.setVenue("Main Hall");
        event.setEventDate(LocalDateTime.of(2026, 5, 10, 20, 0));
        event.setRating(4.5);
        event.setStatus(EventStatus.UPCOMING);
        if (description != null) {
            event.setDetails(Map.of("description", description));
        }
        return event;
    }
}
