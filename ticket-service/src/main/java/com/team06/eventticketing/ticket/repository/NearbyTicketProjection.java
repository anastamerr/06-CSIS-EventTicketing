package com.team06.eventticketing.ticket.repository;

import java.time.LocalDateTime;

public interface NearbyTicketProjection {

    Long getTicketId();

    Long getBookingId();

    Long getEventId();

    String getEventName();

    String getVenue();

    String getAttendeeName();

    String getTicketCode();

    String getTicketStatus();

    LocalDateTime getIssuedAt();

    Double getVenueLatitude();

    Double getVenueLongitude();

    Double getDistanceKm();
}
