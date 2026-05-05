package com.team06.eventticketing.event.adapter;

import com.team06.eventticketing.event.search.EventSearchDocument;
import java.util.Map;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchHitAdapter {

    public EventSearchDocument adapt(SearchHit<?> hit) {
        EventSearchDocument document = new EventSearchDocument();
        if (hit == null) {
            return document;
        }
        document.setId(hit.getId());
        Object content = hit.getContent();
        if (content instanceof EventSearchDocument existing) {
            return existing;
        }
        if (content instanceof Map<?, ?> source) {
            document.setName(asString(source.get("name")));
            document.setCategory(asString(firstPresent(source, "category", "specialty")));
            document.setVenue(asString(source.get("venue")));
            document.setDescription(asString(source.get("description")));
            document.setStatus(asString(source.get("status")));
            document.setRating(asDouble(source.get("rating")));
        }
        return document;
    }

    private Object firstPresent(Map<?, ?> source, String firstKey, String secondKey) {
        Object first = source.get(firstKey);
        return first == null ? source.get(secondKey) : first;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return null;
    }
}
