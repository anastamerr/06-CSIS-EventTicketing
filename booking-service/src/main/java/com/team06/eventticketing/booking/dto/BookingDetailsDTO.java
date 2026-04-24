package com.team06.eventticketing.booking.dto;

import com.team06.eventticketing.booking.model.BookingStatus;
import java.util.List;
import java.util.Map;

public class BookingDetailsDTO {

    private final Long bookingId;
    private final Long userId;
    private final Long eventId;
    private final BookingStatus status;
    private final Double totalAmount;
    private final Map<String, Object> metadata;
    private final List<BookingDetailsItemDTO> items;
    private final int totalItems;
    private final int confirmedItems;

    public BookingDetailsDTO(
            Long bookingId,
            Long userId,
            Long eventId,
            BookingStatus status,
            Double totalAmount,
            Map<String, Object> metadata,
            List<BookingDetailsItemDTO> items,
            int totalItems,
            int confirmedItems
    ) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.eventId = eventId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.metadata = metadata;
        this.items = items;
        this.totalItems = totalItems;
        this.confirmedItems = confirmedItems;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<BookingDetailsItemDTO> getItems() {
        return items;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getConfirmedItems() {
        return confirmedItems;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long bookingId;
        private Long userId;
        private Long eventId;
        private BookingStatus status;
        private Double totalAmount;
        private Map<String, Object> metadata;
        private List<BookingDetailsItemDTO> items;
        private int totalItems;
        private int confirmedItems;

        public Builder bookingId(Long bookingId) {
            this.bookingId = bookingId;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder status(BookingStatus status) {
            this.status = status;
            return this;
        }

        public Builder totalAmount(Double totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder items(List<BookingDetailsItemDTO> items) {
            this.items = items;
            return this;
        }

        public Builder totalItems(int totalItems) {
            this.totalItems = totalItems;
            return this;
        }

        public Builder confirmedItems(int confirmedItems) {
            this.confirmedItems = confirmedItems;
            return this;
        }

        public BookingDetailsDTO build() {
            return new BookingDetailsDTO(
                    bookingId,
                    userId,
                    eventId,
                    status,
                    totalAmount,
                    metadata,
                    items,
                    totalItems,
                    confirmedItems);
        }
    }
}
