package com.team06.eventticketing.gateway.routes;

import com.team06.eventticketing.contracts.messaging.EventTicketingMessagingContracts;
import java.util.List;

public final class BookingServiceRouteOwnership {

    public static final String ROUTE_ID = "booking-service";
    public static final String BOOKING_SERVICE_URI = "http://booking-service:8080";
    public static final String BOOKING_EVENTS_EXCHANGE =
            EventTicketingMessagingContracts.BOOKING_EVENTS_EXCHANGE;
    public static final String BOOKING_SAGA_FEEDBACK_QUEUE =
            EventTicketingMessagingContracts.BOOKING_SAGA_FEEDBACK_QUEUE;
    public static final String BOOKING_SAGA_FEEDBACK_DLQ =
            EventTicketingMessagingContracts.BOOKING_SAGA_FEEDBACK_DLQ;
    public static final List<String> OWNED_PATH_PATTERNS = List.of("/api/bookings/**");
    public static final List<String> COVERED_BOOKING_ENDPOINTS = List.of(
            "GET /api/bookings/{id}",
            "GET /api/bookings/user/{userId}/summary",
            "GET /api/bookings/user/{userId}/active-count",
            "GET /api/bookings/user/{userId}/count",
            "GET /api/bookings/user/{userId}/total",
            "GET /api/bookings/event/{eventId}/revenue",
            "GET /api/bookings/event/{eventId}/active-count",
            "PUT /api/bookings/{bookingId}/confirm",
            "PUT /api/bookings/{id}/complete",
            "PUT /api/bookings/{id}/cancel");

    private BookingServiceRouteOwnership() {
    }
}
