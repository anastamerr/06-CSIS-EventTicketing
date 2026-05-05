package com.team06.eventticketing.ticket.dto;

import java.time.LocalDateTime;

public class IdleProviderDTO {

    private Long ticketId;
    private String attendeeName;
    private String ticketCode;
    private Long bookingId;
    private String eventName;
    private LocalDateTime eventDate;

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public void setAttendeeName(String attendeeName) {
        this.attendeeName = attendeeName;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final IdleProviderDTO value = new IdleProviderDTO();

        public Builder ticketId(Long ticketId) {
            value.setTicketId(ticketId);
            return this;
        }

        public Builder attendeeName(String attendeeName) {
            value.setAttendeeName(attendeeName);
            return this;
        }

        public Builder ticketCode(String ticketCode) {
            value.setTicketCode(ticketCode);
            return this;
        }

        public Builder bookingId(Long bookingId) {
            value.setBookingId(bookingId);
            return this;
        }

        public Builder eventName(String eventName) {
            value.setEventName(eventName);
            return this;
        }

        public Builder eventDate(LocalDateTime eventDate) {
            value.setEventDate(eventDate);
            return this;
        }

        public IdleProviderDTO build() {
            return value;
        }
    }
}
