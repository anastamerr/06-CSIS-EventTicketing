package com.team06.eventticketing.event.dto;

import com.team06.eventticketing.event.model.EventSession;
import com.team06.eventticketing.event.model.EventStatus;
import java.util.List;

public class EventSessionAlertDTO {

    private Long eventId;
    private String eventName;
    private EventStatus eventStatus;
    private List<EventSession> unverifiedSessions;
    private int unverifiedCount;

    public EventSessionAlertDTO() {
    }

    public EventSessionAlertDTO(
            Long eventId,
            String eventName,
            EventStatus eventStatus,
            List<EventSession> unverifiedSessions,
            int unverifiedCount
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventStatus = eventStatus;
        this.unverifiedSessions = unverifiedSessions;
        this.unverifiedCount = unverifiedCount;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public EventStatus getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(EventStatus eventStatus) {
        this.eventStatus = eventStatus;
    }

    public List<EventSession> getUnverifiedSessions() {
        return unverifiedSessions;
    }

    public void setUnverifiedSessions(List<EventSession> unverifiedSessions) {
        this.unverifiedSessions = unverifiedSessions;
    }

    public int getUnverifiedCount() {
        return unverifiedCount;
    }

    public void setUnverifiedCount(int unverifiedCount) {
        this.unverifiedCount = unverifiedCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long eventId;
        private String eventName;
        private EventStatus eventStatus;
        private List<EventSession> unverifiedSessions;
        private int unverifiedCount;

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventName(String eventName) {
            this.eventName = eventName;
            return this;
        }

        public Builder eventStatus(EventStatus eventStatus) {
            this.eventStatus = eventStatus;
            return this;
        }

        public Builder unverifiedSessions(List<EventSession> unverifiedSessions) {
            this.unverifiedSessions = unverifiedSessions;
            return this;
        }

        public Builder unverifiedCount(int unverifiedCount) {
            this.unverifiedCount = unverifiedCount;
            return this;
        }

        public EventSessionAlertDTO build() {
            return new EventSessionAlertDTO(eventId, eventName, eventStatus, unverifiedSessions, unverifiedCount);
        }
    }
}
