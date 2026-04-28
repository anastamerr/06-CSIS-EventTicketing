package com.team06.eventticketing.event.search;

import com.team06.eventticketing.event.model.Event;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

@Service
public class EventSearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(EventSearchSyncService.class);

    private final ElasticsearchOperations elasticsearchOperations;

    public EventSearchSyncService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public void indexEvent(Event event) {
        try {
            EventSearchDocument document = new EventSearchDocument();
            document.setId(String.valueOf(event.getId()));
            document.setName(event.getName());
            document.setCategory(event.getCategory() == null ? null : event.getCategory().name());
            document.setVenue(event.getVenue());
            document.setDescription(resolveDescription(event.getDetails()));
            document.setEventDate(event.getEventDate());
            document.setRating(event.getRating());
            document.setStatus(event.getStatus() == null ? null : event.getStatus().name());
            elasticsearchOperations.save(document);
        } catch (Exception exception) {
            log.warn("Elasticsearch indexing failed for event {}", event == null ? null : event.getId(), exception);
        }
    }

    public void removeEvent(Long eventId) {
        try {
            elasticsearchOperations.delete(String.valueOf(eventId), EventSearchDocument.class);
        } catch (Exception exception) {
            log.warn("Elasticsearch delete failed for event {}", eventId, exception);
        }
    }

    private String resolveDescription(Map<String, Object> details) {
        Map<String, Object> safe = details == null ? new LinkedHashMap<>() : details;
        Object description = safe.get("description");
        return description == null ? "" : description.toString();
    }
}
