package com.team06.eventticketing.gateway.routes;

import java.util.List;

public final class EventServiceRouteOwnership {

    public static final String ROUTE_ID = "event-service";
    public static final String EVENT_SERVICE_URI = "http://event-service:8080";
    public static final String EVENT_EVENTS_EXCHANGE = "event.events";
    public static final String EVENT_STATUS_CHANGED_ROUTING_KEY = "event.status-changed";
    public static final String EVENT_RATED_ROUTING_KEY = "event.rated";
    public static final String EVENT_BOOKING_SAGA_QUEUE = "event.booking.saga-listener";
    public static final String EVENT_BOOKING_SAGA_DLQ = "event.booking.saga-listener.dlq";

    public static final List<String> OWNED_PATH_PATTERNS = List.of(
            "/api/events/**",
            "/api/events/{id}",
            "/api/events/{id}/status",
            "/api/events/{id}/rate",
            "/api/events/{id}/dashboard",
            "/api/events/{id}/sessions/avg-capacity",
            "/api/events/{id}/venue-coords"
    );

    private EventServiceRouteOwnership() {
    }
}