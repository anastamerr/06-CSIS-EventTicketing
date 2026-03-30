package com.team06.eventticketing.event.dto;

import com.team06.eventticketing.event.model.EventStatus;

public class UpdateEventStatusRequest {

    private EventStatus status;

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }
}
