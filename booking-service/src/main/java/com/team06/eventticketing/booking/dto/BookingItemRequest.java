package com.team06.eventticketing.booking.dto;

import com.team06.eventticketing.booking.model.BookingItemStatus;
import java.util.Map;

public class BookingItemRequest {

    private Integer eventOrder;
    private Long sessionId;
    private String sessionTitle;
    private Integer quantity;
    private Double unitPrice;
    private BookingItemStatus status;
    private Map<String, Object> metadata;

    public Integer getEventOrder() {
        return eventOrder;
    }

    public void setEventOrder(Integer eventOrder) {
        this.eventOrder = eventOrder;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BookingItemStatus getStatus() {
        return status;
    }

    public void setStatus(BookingItemStatus status) {
        this.status = status;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
