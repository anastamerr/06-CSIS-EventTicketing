package com.team06.eventticketing.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.repository.EventRepository;
import com.team06.eventticketing.event.repository.EventSessionRepository;
import com.team06.eventticketing.event.search.EventFullTextSearchService;
import com.team06.eventticketing.event.search.EventSearchSyncService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EventServiceIndexingTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventSessionRepository eventSessionRepository;

    @Mock
    private EventSearchSyncService eventSearchSyncService;

    @Mock
    private EventFullTextSearchService eventFullTextSearchService;

    @Mock
    private EntityObserver observer;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(
                eventRepository,
                eventSessionRepository,
                eventSearchSyncService,
                eventFullTextSearchService);
        eventService.register(observer);
    }

    @Test
    void indexEventForSearchIndexesExistingEventAndLogsExplicitSource() {
        Event event = event(10L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        eventService.indexEventForSearch(10L);

        verify(eventSearchSyncService).indexEvent(event);
        assertIndexedEvent("explicit");
    }

    @Test
    void indexEventForSearchRejectsUnknownEvent() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.indexEventForSearch(999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void createEventAutoIndexesAndLogsCreateSource() {
        Event event = event(10L);
        when(eventRepository.save(event)).thenReturn(event);

        eventService.createEvent(event);

        verify(eventSearchSyncService).indexEvent(event);
        assertIndexedEvent("auto_crud_create");
    }

    @Test
    void updateEventAutoIndexesAndLogsUpdateSource() {
        Event existing = event(10L);
        Event update = event(10L);
        update.setName("Updated Broadway Night");

        when(eventRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(eventRepository.save(existing)).thenReturn(existing);

        eventService.updateEvent(10L, update);

        verify(eventSearchSyncService).indexEvent(existing);
        assertIndexedEvent("auto_crud_update");
    }

    @Test
    void deleteEventRemovesSearchDocumentBeforeLoggingDeletedEvent() {
        Event event = event(10L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        eventService.deleteEvent(10L);

        InOrder inOrder = inOrder(eventSearchSyncService, observer, eventRepository);
        inOrder.verify(eventSearchSyncService).removeEvent(10L);
        inOrder.verify(observer).onEvent(eq("EVENT_DELETED"), org.mockito.Mockito.any());
        inOrder.verify(eventRepository).deleteById(10L);
    }

    @SuppressWarnings("unchecked")
    private void assertIndexedEvent(String source) {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(observer).onEvent(eq("INDEXED"), payloadCaptor.capture());

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        Map<String, Object> details = (Map<String, Object>) payload.get("details");

        assertEquals(10L, payload.get("eventId"));
        assertEquals(10L, details.get("eventId"));
        assertEquals(List.of("id", "name", "category", "venue", "description", "eventDate", "rating", "status"),
                details.get("indexedFields"));
        assertEquals(source, details.get("source"));
    }

    private Event event(Long id) {
        Event event = new Event();
        event.setId(id);
        event.setName("Broadway Night");
        event.setVenue("Main Hall");
        return event;
    }
}
