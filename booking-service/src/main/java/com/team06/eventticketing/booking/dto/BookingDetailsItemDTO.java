package com.team06.eventticketing.booking.dto;

import com.team06.eventticketing.booking.model.BookingItemStatus;
import java.util.Map;

public class BookingDetailsItemDTO {

    private final Long id;
    private final Integer eventOrder;
    private final String sessionTitle;
    private final Integer quantity;
    private final Double unitPrice;
    private final BookingItemStatus status;
    private final Map<String, Object> metadata;

    public BookingDetailsItemDTO(
            Long id,
            Integer eventOrder,
            String sessionTitle,
            Integer quantity,
            Double unitPrice,
            BookingItemStatus status,
            Map<String, Object> metadata
    ) {
        this.id = id;
        this.eventOrder = eventOrder;
        this.sessionTitle = sessionTitle;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.status = status;
        this.metadata = metadata;
    }

    public Long getId() {
        return id;
    }

    public Integer getEventOrder() {
        return eventOrder;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public BookingItemStatus getStatus() {
        return status;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long id;
        private Integer eventOrder;
        private String sessionTitle;
        private Integer quantity;
        private Double unitPrice;
        private BookingItemStatus status;
        private Map<String, Object> metadata;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder eventOrder(Integer eventOrder) {
            this.eventOrder = eventOrder;
            return this;
        }

        public Builder sessionTitle(String sessionTitle) {
            this.sessionTitle = sessionTitle;
            return this;
        }

        public Builder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unitPrice(Double unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder status(BookingItemStatus status) {
            this.status = status;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public BookingDetailsItemDTO build() {
            return new BookingDetailsItemDTO(id, eventOrder, sessionTitle, quantity, unitPrice, status, metadata);
        }
    }
}
