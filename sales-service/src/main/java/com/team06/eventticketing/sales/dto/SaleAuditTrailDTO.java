package com.team06.eventticketing.sales.dto;

import java.util.ArrayList;
import java.util.List;

public class SaleAuditTrailDTO {

    private Long saleId;
    private List<AuditEventDTO> events = new ArrayList<>();

    public Long getSaleId() {
        return saleId;
    }

    public void setSaleId(Long saleId) {
        this.saleId = saleId;
    }

    public List<AuditEventDTO> getEvents() {
        return events;
    }

    public void setEvents(List<AuditEventDTO> events) {
        this.events = events == null ? new ArrayList<>() : events;
    }
}
