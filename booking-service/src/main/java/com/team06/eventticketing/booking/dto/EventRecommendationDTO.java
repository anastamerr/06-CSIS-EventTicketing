package com.team06.eventticketing.booking.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class EventRecommendationDTO {

    private final Long eventId;
    private final String name;
    private final String category;
    private final LocalDateTime eventDate;
    private final long score;

    @JsonCreator
    public EventRecommendationDTO(
            @JsonProperty("eventId") Long eventId,
            @JsonProperty("name") String name,
            @JsonProperty("category") String category,
            @JsonProperty("eventDate") LocalDateTime eventDate,
            @JsonProperty("score") long score) {
        this.eventId = eventId;
        this.name = name;
        this.category = category;
        this.eventDate = eventDate;
        this.score = score;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public long getScore() {
        return score;
    }
}
