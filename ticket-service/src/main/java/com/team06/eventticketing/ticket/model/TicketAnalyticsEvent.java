package com.team06.eventticketing.ticket.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ticket_events")
public class TicketAnalyticsEvent {

    @Id
    private String id;
    private String eventType;
    private String userEmail;
    private LocalDate viewedStartDate;
    private LocalDate viewedEndDate;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;

    public TicketAnalyticsEvent() {
    }

    public TicketAnalyticsEvent(String eventType,
                                String userEmail,
                                LocalDate viewedStartDate,
                                LocalDate viewedEndDate,
                                LocalDateTime createdAt,
                                Map<String, Object> metadata) {
        this.eventType = eventType;
        this.userEmail = userEmail;
        this.viewedStartDate = viewedStartDate;
        this.viewedEndDate = viewedEndDate;
        this.createdAt = createdAt;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public LocalDate getViewedStartDate() {
        return viewedStartDate;
    }

    public void setViewedStartDate(LocalDate viewedStartDate) {
        this.viewedStartDate = viewedStartDate;
    }

    public LocalDate getViewedEndDate() {
        return viewedEndDate;
    }

    public void setViewedEndDate(LocalDate viewedEndDate) {
        this.viewedEndDate = viewedEndDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
