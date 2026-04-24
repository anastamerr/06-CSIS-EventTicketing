package com.team06.eventticketing.event.service;

import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventSession;
import com.team06.eventticketing.event.repository.EventRepository;
import com.team06.eventticketing.event.repository.EventSessionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventSessionService {

    private final EventRepository eventRepository;
    private final EventSessionRepository eventSessionRepository;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    public EventSessionService(
            EventRepository eventRepository,
            EventSessionRepository eventSessionRepository,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.eventRepository = eventRepository;
        this.eventSessionRepository = eventSessionRepository;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

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
        EventSession saved = eventSessionRepository.save(session);
        notifyObservers("EVENT_SESSION_CREATED", Map.of(
                "eventId", eventId,
                "details", buildSessionDetails(saved)));
        return saved;
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
        EventSession saved = eventSessionRepository.save(existingSession);
        notifyObservers("EVENT_SESSION_UPDATED", Map.of(
                "eventId", eventId,
                "details", buildSessionDetails(saved)));
        return saved;
    }

    public void deleteSession(Long eventId, Long sessionId) {
        EventSession session = getSession(eventId, sessionId);
        notifyObservers("EVENT_SESSION_DELETED", Map.of(
                "eventId", eventId,
                "details", buildSessionDetails(session)));
        eventSessionRepository.delete(session);
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String action, Object payload) {
        observers.forEach(observer -> observer.onEvent(action, payload));
    }

    private Map<String, Object> buildSessionDetails(EventSession session) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sessionId", session.getId());
        details.put("title", session.getTitle());
        details.put("verified", session.getVerified());
        return details;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.EVENT_ACTIVITY, "event_events"));
        }
    }
}
