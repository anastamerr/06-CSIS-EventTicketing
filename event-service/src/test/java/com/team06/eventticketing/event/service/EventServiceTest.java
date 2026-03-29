package com.team06.eventticketing.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.event.dto.VerifyEventSessionRequest;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventSession;
import com.team06.eventticketing.event.repository.EventRepository;
import com.team06.eventticketing.event.repository.EventSessionRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventSessionRepository eventSessionRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, eventSessionRepository);
    }

    @Test
    void verifyEventSessionMarksSessionVerifiedAndUpdatesMetadata() {
        Event event = new Event();
        event.setId(10L);

        EventSession session = session(20L, event, LocalDateTime.now().plusDays(1));
        Event loadedEvent = new Event();
        loadedEvent.setId(10L);
        loadedEvent.setEventSessions(List.of(session));

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventSessionRepository.findByIdWithEvent(20L)).thenReturn(Optional.of(session));
        when(eventRepository.existsAdminUserById(3L)).thenReturn(true);
        when(eventRepository.findByIdWithEventSessions(10L)).thenReturn(Optional.of(loadedEvent));

        VerifyEventSessionRequest request = new VerifyEventSessionRequest();
        request.setVerifiedBy(3L);

        Event result = eventService.verifyEventSession(10L, 20L, request);

        assertEquals(Boolean.TRUE, session.getVerified());
        assertEquals(3L, session.getMetadata().get("verifiedBy"));
        assertNotNull(session.getMetadata().get("verifiedAt"));
        assertEquals(loadedEvent, result);
        verify(eventSessionRepository).save(session);
    }

    @Test
    void verifyEventSessionRejectsPastSession() {
        Event event = new Event();
        event.setId(10L);
        EventSession session = session(20L, event, LocalDateTime.now().minusHours(1));

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventSessionRepository.findByIdWithEvent(20L)).thenReturn(Optional.of(session));

        VerifyEventSessionRequest request = new VerifyEventSessionRequest();
        request.setVerifiedBy(3L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.verifyEventSession(10L, 20L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(eventRepository, never()).existsAdminUserById(3L);
    }

    @Test
    void verifyEventSessionRejectsNonAdminVerifier() {
        Event event = new Event();
        event.setId(10L);
        EventSession session = session(20L, event, LocalDateTime.now().plusHours(2));

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventSessionRepository.findByIdWithEvent(20L)).thenReturn(Optional.of(session));
        when(eventRepository.existsAdminUserById(7L)).thenReturn(false);

        VerifyEventSessionRequest request = new VerifyEventSessionRequest();
        request.setVerifiedBy(7L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.verifyEventSession(10L, 20L, request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(eventSessionRepository, never()).save(session);
    }

    @Test
    void verifyEventSessionRejectsSessionFromDifferentEvent() {
        Event requestedEvent = new Event();
        requestedEvent.setId(10L);
        Event otherEvent = new Event();
        otherEvent.setId(99L);
        EventSession session = session(20L, otherEvent, LocalDateTime.now().plusHours(2));

        when(eventRepository.findById(10L)).thenReturn(Optional.of(requestedEvent));
        when(eventSessionRepository.findByIdWithEvent(20L)).thenReturn(Optional.of(session));

        VerifyEventSessionRequest request = new VerifyEventSessionRequest();
        request.setVerifiedBy(3L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.verifyEventSession(10L, 20L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(eventRepository, never()).existsAdminUserById(3L);
    }

    private EventSession session(Long id, Event event, LocalDateTime startTime) {
        EventSession session = new EventSession();
        session.setId(id);
        session.setEvent(event);
        session.setTitle("Session");
        session.setStartTime(startTime);
        session.setEndTime(startTime.plusHours(1));
        session.setCapacity(100);
        session.setVerified(Boolean.FALSE);
        session.setMetadata(new LinkedHashMap<>());
        return session;
    }
}
