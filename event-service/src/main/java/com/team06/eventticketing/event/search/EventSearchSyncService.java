package com.team06.eventticketing.event.search;

import com.team06.eventticketing.event.adapter.EventSearchDocumentAdapter;
import com.team06.eventticketing.event.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

@Service
public class EventSearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(EventSearchSyncService.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final EventSearchDocumentAdapter eventSearchDocumentAdapter;

    public EventSearchSyncService(
            ElasticsearchOperations elasticsearchOperations,
            EventSearchDocumentAdapter eventSearchDocumentAdapter
    ) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.eventSearchDocumentAdapter = eventSearchDocumentAdapter;
    }

    public void indexEvent(Event event) {
        try {
            EventSearchDocument document = eventSearchDocumentAdapter.adapt(event);
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
}
