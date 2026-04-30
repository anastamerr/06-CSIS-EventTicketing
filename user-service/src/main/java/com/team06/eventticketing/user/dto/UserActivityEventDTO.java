package com.team06.eventticketing.user.dto;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserActivityEventDTO {

    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details = new LinkedHashMap<>();

    public UserActivityEventDTO() {
    }

    public UserActivityEventDTO(String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.action = action;
        this.timestamp = timestamp;
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }
}
