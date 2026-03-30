package com.team06.eventticketing.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.event.dto.EventRevenueDTO;
import com.team06.eventticketing.event.dto.EventSessionAlertDTO;
import com.team06.eventticketing.event.dto.RateEventRequest;
import com.team06.eventticketing.event.dto.UpdateEventStatusRequest;
import com.team06.eventticketing.event.dto.VerifyEventSessionRequest;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventSession;
import com.team06.eventticketing.event.model.EventStatus;
import com.team06.eventticketing.event.repository.EventRepository;
import com.team06.eventticketing.event.repository.EventSessionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    void getEventRevenueSummaryReturnsAggregatedMetrics() {
        Event event = new Event();
        event.setId(10L);
        event.setName("Spring Concert");

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.findEventRevenueSummary(
                10L,
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 1).atStartOfDay()
        )).thenReturn(java.util.Collections.<Object[]>singletonList(new Object[]{10L, "Spring Concert", 5L, 3500.0, 700.0}));

        EventRevenueDTO dto = eventService.getEventRevenueSummary(
                10L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(10L, dto.getEventId());
        assertEquals("Spring Concert", dto.getName());
        assertEquals(5L, dto.getTotalBookings());
        assertEquals(3500.0, dto.getTotalRevenue());
        assertEquals(700.0, dto.getAverageBookingAmount());
    }

    @Test
    void getEventRevenueSummaryReturnsZeroesWhenNoBookingsExist() {
        Event event = new Event();
        event.setId(10L);
        event.setName("Spring Concert");

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.findEventRevenueSummary(
                10L,
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 1).atStartOfDay()
        )).thenReturn(java.util.Collections.<Object[]>singletonList(new Object[]{10L, "Spring Concert", 0L, 0.0, 0.0}));

        EventRevenueDTO dto = eventService.getEventRevenueSummary(
                10L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(0L, dto.getTotalBookings());
        assertEquals(0.0, dto.getTotalRevenue());
        assertEquals(0.0, dto.getAverageBookingAmount());
    }

    @Test
    void getEventRevenueSummaryRejectsUnknownEvent() {
        when(eventRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.getEventRevenueSummary(404L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void rateEventUpdatesRunningAverage() {
        Event event = new Event();
        event.setId(10L);
        event.setRating(5.0);
        event.setTotalRatings(1);

        RateEventRequest request = new RateEventRequest();
        request.setBookingId(20L);
        request.setRating(3);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.findBookingById(20L))
                .thenReturn(java.util.Collections.<Object[]>singletonList(new Object[]{20L, 10L, "COMPLETED"}));

        eventService.rateEvent(10L, request);

        assertEquals(4.0, event.getRating());
        assertEquals(2, event.getTotalRatings());
        verify(eventRepository).save(event);
    }

    @Test
    void rateEventRejectsUnknownBooking() {
        Event event = new Event();
        event.setId(10L);

        RateEventRequest request = new RateEventRequest();
        request.setBookingId(20L);
        request.setRating(4);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.findBookingById(20L)).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.rateEvent(10L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void rateEventRejectsBookingForDifferentEvent() {
        Event event = new Event();
        event.setId(10L);

        RateEventRequest request = new RateEventRequest();
        request.setBookingId(20L);
        request.setRating(4);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.findBookingById(20L))
                .thenReturn(java.util.Collections.<Object[]>singletonList(new Object[]{20L, 99L, "COMPLETED"}));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.rateEvent(10L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(eventRepository, never()).save(event);
    }

    @Test
    void rateEventRejectsNonCompletedBooking() {
        Event event = new Event();
        event.setId(10L);

        RateEventRequest request = new RateEventRequest();
        request.setBookingId(20L);
        request.setRating(4);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.findBookingById(20L))
                .thenReturn(java.util.Collections.<Object[]>singletonList(new Object[]{20L, 10L, "PENDING"}));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.rateEvent(10L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(eventRepository, never()).save(event);
    }

    @Test
    void rateEventRejectsOutOfRangeRating() {
        Event event = new Event();
        event.setId(10L);

        RateEventRequest request = new RateEventRequest();
        request.setBookingId(20L);
        request.setRating(6);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.findBookingById(20L))
                .thenReturn(java.util.Collections.<Object[]>singletonList(new Object[]{20L, 10L, "COMPLETED"}));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.rateEvent(10L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(eventRepository, never()).save(event);
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

    @Test
    void updateEventStatusCancelsEventWhenNoActiveBookingsExist() {
        Event event = new Event();
        event.setId(10L);
        event.setStatus(EventStatus.UPCOMING);

        UpdateEventStatusRequest request = new UpdateEventStatusRequest();
        request.setStatus(EventStatus.CANCELLED);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.existsActiveBookingsForEvent(10L)).thenReturn(false);

        eventService.updateEventStatus(10L, request);

        assertEquals(EventStatus.CANCELLED, event.getStatus());
        verify(eventRepository).save(event);
    }

    @Test
    void updateEventStatusRejectsCancellationWhenActiveBookingsExist() {
        Event event = new Event();
        event.setId(10L);
        event.setStatus(EventStatus.UPCOMING);

        UpdateEventStatusRequest request = new UpdateEventStatusRequest();
        request.setStatus(EventStatus.CANCELLED);

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.existsActiveBookingsForEvent(10L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.updateEventStatus(10L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(eventRepository, never()).save(event);
    }

    @Test
    void updateEventStatusRejectsMissingStatus() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.updateEventStatus(10L, new UpdateEventStatusRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(eventRepository, never()).findById(10L);
    }

    @Test
    void updateEventStatusRejectsUnknownEvent() {
        UpdateEventStatusRequest request = new UpdateEventStatusRequest();
        request.setStatus(EventStatus.ONGOING);

        when(eventRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.updateEventStatus(404L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any(Event.class));
    }

    @Test
    void getEventsWithUnverifiedSessionsReturnsOnlyUnverifiedSessionsPerEvent() {
        Event firstEvent = new Event();
        firstEvent.setId(10L);
        firstEvent.setName("Event A");
        firstEvent.setStatus(EventStatus.UPCOMING);
        EventSession unverified = session(20L, firstEvent, LocalDateTime.now().plusDays(1));
        EventSession verified = session(21L, firstEvent, LocalDateTime.now().plusDays(2));
        verified.setVerified(Boolean.TRUE);
        firstEvent.setEventSessions(List.of(unverified, verified));

        Event secondEvent = new Event();
        secondEvent.setId(11L);
        secondEvent.setName("Event C");
        secondEvent.setStatus(EventStatus.ONGOING);
        EventSession secondUnverified = session(22L, secondEvent, LocalDateTime.now().plusDays(3));
        secondEvent.setEventSessions(List.of(secondUnverified));

        when(eventRepository.findEventsWithUnverifiedSessions()).thenReturn(List.of(firstEvent, secondEvent));

        List<EventSessionAlertDTO> result = eventService.getEventsWithUnverifiedSessions();

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).getEventId());
        assertEquals("Event A", result.get(0).getEventName());
        assertEquals(EventStatus.UPCOMING, result.get(0).getEventStatus());
        assertEquals(1, result.get(0).getUnverifiedCount());
        assertEquals(List.of(unverified), result.get(0).getUnverifiedSessions());
        assertEquals(11L, result.get(1).getEventId());
        assertEquals(1, result.get(1).getUnverifiedCount());
        assertEquals(List.of(secondUnverified), result.get(1).getUnverifiedSessions());
    }

    @Test
    void getEventsWithUnverifiedSessionsReturnsEmptyListWhenRepositoryHasNoMatches() {
        when(eventRepository.findEventsWithUnverifiedSessions()).thenReturn(List.of());

        List<EventSessionAlertDTO> result = eventService.getEventsWithUnverifiedSessions();

        assertEquals(List.of(), result);
    }

    @Test
    void updateEventDetailsMergesIncomingJsonbFields() {
        Event event = new Event();
        event.setId(10L);
        event.setDetails(new LinkedHashMap<>(Map.of(
                "organizer", "LiveNation",
                "venueCapacity", 5000,
                "ageRestriction", 18
        )));

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        Event updated = eventService.updateEventDetails(10L, new LinkedHashMap<>(Map.of(
                "venueCapacity", 8000,
                "sponsors", List.of("Pepsi", "Samsung")
        )));

        assertEquals("LiveNation", updated.getDetails().get("organizer"));
        assertEquals(8000, updated.getDetails().get("venueCapacity"));
        assertEquals(18, updated.getDetails().get("ageRestriction"));
        assertEquals(List.of("Pepsi", "Samsung"), updated.getDetails().get("sponsors"));
        verify(eventRepository).save(event);
    }

    @Test
    void updateEventDetailsThrowsNotFoundForUnknownEvent() {
        when(eventRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> eventService.updateEventDetails(404L, Map.of("organizer", "AEG")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void findByDetailAttributeUsesOptionalStatusFilter() {
        Event upcoming = new Event();
        upcoming.setId(1L);
        upcoming.setStatus(EventStatus.UPCOMING);

        Event cancelled = new Event();
        cancelled.setId(2L);
        cancelled.setStatus(EventStatus.CANCELLED);

        when(eventRepository.findByDetailsAttribute("organizer", "LiveNation"))
                .thenReturn(List.of(upcoming, cancelled));
        when(eventRepository.findByDetailsAttributeAndStatus("organizer", "LiveNation", "UPCOMING"))
                .thenReturn(List.of(upcoming));

        List<Event> allStatuses = eventService.findByDetailAttribute("organizer", "LiveNation", null);
        List<Event> onlyUpcoming = eventService.findByDetailAttribute("organizer", "LiveNation", EventStatus.UPCOMING);

        assertEquals(2, allStatuses.size());
        assertEquals(1, onlyUpcoming.size());
        assertEquals(1L, onlyUpcoming.get(0).getId());
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
