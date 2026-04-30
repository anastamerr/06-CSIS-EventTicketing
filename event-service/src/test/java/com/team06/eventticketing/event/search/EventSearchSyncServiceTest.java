package com.team06.eventticketing.event.search;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.event.adapter.EventSearchDocumentAdapter;
import com.team06.eventticketing.event.model.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@ExtendWith(MockitoExtension.class)
class EventSearchSyncServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private EventSearchDocumentAdapter eventSearchDocumentAdapter;

    @Test
    void indexEventSavesAdaptedDocument() {
        Event event = new Event();
        event.setId(10L);
        EventSearchDocument document = new EventSearchDocument();
        document.setId("10");

        when(eventSearchDocumentAdapter.adapt(event)).thenReturn(document);

        new EventSearchSyncService(elasticsearchOperations, eventSearchDocumentAdapter).indexEvent(event);

        verify(elasticsearchOperations).save(document);
    }

    @Test
    void removeEventDeletesSearchDocument() {
        new EventSearchSyncService(elasticsearchOperations, eventSearchDocumentAdapter).removeEvent(10L);

        verify(elasticsearchOperations).delete("10", EventSearchDocument.class);
    }
}
