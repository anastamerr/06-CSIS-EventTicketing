package com.team06.eventticketing.event.service;

import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventSession;
import com.team06.eventticketing.event.repository.EventRepository;
import com.team06.eventticketing.event.repository.EventSessionRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventSessionService {

    private final EventRepository eventRepository;
    private final EventSessionRepository eventSessionRepository;

    public EventSessionService(EventRepository eventRepository, EventSessionRepository eventSessionRepository) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
    }

    public List<EventSession> getSessions(Long eventId) {
        getEvent(eventId);
        return eventSessionRepository.findByEventIdOrderByStartTimeAsc(eventId);
    }

    public EventSession getSession(Long eventId, Long sessionId) {
        EventSession session = eventSessionRepository.findByIdWithEvent(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getEvent().getId().equals(eventId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session does not belong to the specified event");
        }
        return session;
    }

    public EventSession createSession(Long eventId, EventSession session) {
        Event event = getEvent(eventId);
        session.setEvent(event);
        return eventSessionRepository.save(session);
    }

    public EventSession updateSession(Long eventId, Long sessionId, EventSession updatedSession) {
        EventSession existingSession = getSession(eventId, sessionId);
        existingSession.setTitle(updatedSession.getTitle());
        existingSession.setSpeaker(updatedSession.getSpeaker());
        existingSession.setStartTime(updatedSession.getStartTime());
        existingSession.setEndTime(updatedSession.getEndTime());
        existingSession.setCapacity(updatedSession.getCapacity());
        existingSession.setVerified(updatedSession.getVerified());
        existingSession.setMetadata(updatedSession.getMetadata());
        return eventSessionRepository.save(existingSession);
    }

    public void deleteSession(Long eventId, Long sessionId) {
        EventSession session = getSession(eventId, sessionId);
        eventSessionRepository.delete(session);
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }
}
