package com.team06.eventticketing.ticket.repository;

public interface NearbyTicketProjection {

    Long getTicketId();

    String getAttendeeName();

    String getTicketCode();

    Long getBookingId();

    String getEventName();

    Double getEventLat();

    Double getEventLon();

    Double getDistanceKm();
}
