package com.team06.eventticketing.ticket.repository;

import java.time.LocalDateTime;

public interface UnusedTicketProjection {

    Long getTicketId();

    String getAttendeeName();

    String getTicketCode();

    Long getBookingId();

    String getEventName();

    LocalDateTime getEventDate();
}
