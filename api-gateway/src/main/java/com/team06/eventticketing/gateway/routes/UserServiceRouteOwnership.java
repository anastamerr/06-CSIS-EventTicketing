package com.team06.eventticketing.gateway.routes;

import java.util.List;

public final class UserServiceRouteOwnership {

    public static final String ROUTE_ID = "user-service";
    public static final String USER_SERVICE_URI = "http://user-service:8080";
    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String USER_BOOKING_SAGA_QUEUE = "user.booking.saga-listener";
    public static final String USER_BOOKING_SAGA_DLQ = "user.booking.saga-listener.dlq";
    public static final List<String> OWNED_PATH_PATTERNS = List.of(
            "/api/auth/**",
            "/api/users/**");

    private UserServiceRouteOwnership() {
    }
}
