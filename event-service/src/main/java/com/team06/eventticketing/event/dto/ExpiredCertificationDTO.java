package com.team06.eventticketing.event.dto;

import java.util.List;

public class ExpiredCertificationDTO {

    private Long eventId;
    private String eventName;
    private String eventStatus;
    private List<?> unverifiedSessions;
    private int unverifiedCount;

    public ExpiredCertificationDTO() {
    }

    public ExpiredCertificationDTO(
            Long eventId,
            String eventName,
            String eventStatus,
            List<?> unverifiedSessions,
            int unverifiedCount) {
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

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public List<?> getUnverifiedSessions() {
        return unverifiedSessions;
    }

    public void setUnverifiedSessions(List<?> unverifiedSessions) {
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
        private String eventStatus;
        private List<?> unverifiedSessions;
        private int unverifiedCount;

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventName(String eventName) {
            this.eventName = eventName;
            return this;
        }

        public Builder eventStatus(String eventStatus) {
            this.eventStatus = eventStatus;
            return this;
        }

        public Builder unverifiedSessions(List<?> unverifiedSessions) {
            this.unverifiedSessions = unverifiedSessions;
            return this;
        }

        public Builder unverifiedCount(int unverifiedCount) {
            this.unverifiedCount = unverifiedCount;
            return this;
        }

        public ExpiredCertificationDTO build() {
            return new ExpiredCertificationDTO(eventId, eventName, eventStatus, unverifiedSessions, unverifiedCount);
        }
    }
}
