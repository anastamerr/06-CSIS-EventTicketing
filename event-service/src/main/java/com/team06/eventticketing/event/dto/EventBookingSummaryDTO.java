package com.team06.eventticketing.event.dto;

import java.time.LocalDateTime;

public class EventBookingSummaryDTO {
    private Long eventId;
    private String name;
    private String category;
    private String status;
    private LocalDateTime eventDate;

    public EventBookingSummaryDTO(Long eventId, String name, String category, String status, LocalDateTime eventDate) {
        this.eventId = eventId;
        this.name = name;
        this.category = category;
        this.status = status;
        this.eventDate = eventDate;
    }

    public Long getEventId() { return eventId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getStatus() { return status; }
    public LocalDateTime getEventDate() { return eventDate; }
}