package com.team06.eventticketing.ticket.dto;

public class NearbyTicketResponseDTO {

    private Long ticketId;
    private String attendeeName;
    private String ticketCode;
    private Long bookingId;
    private String eventName;
    private Double eventLat;
    private Double eventLon;
    private Double distanceKm;

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

    public Double getEventLat() {
        return eventLat;
    }

    public void setEventLat(Double eventLat) {
        this.eventLat = eventLat;
    }

    public Double getEventLon() {
        return eventLon;
    }

    public void setEventLon(Double eventLon) {
        this.eventLon = eventLon;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }
}
