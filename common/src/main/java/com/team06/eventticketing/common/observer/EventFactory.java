package com.team06.eventticketing.common.observer;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        Map<String, Object> details = copyDetails(params.get("details"));
        String action = stringValue(params.get("action"));
        LocalDateTime timestamp = (LocalDateTime) params.getOrDefault("timestamp", LocalDateTime.now());

        return switch (type) {
            case AUTH -> {
                AuthEvent event = new AuthEvent();
                event.setUserId(longValue(params.get("userId")));
                event.setAction(action);
                event.setTimestamp(timestamp);
                event.setDetails(details);
                yield event;
            }
            case EVENT_ACTIVITY -> {
                EventActivityEvent event = new EventActivityEvent();
                event.setEventId(longValue(params.get("eventId")));
                event.setAction(action);
                event.setTimestamp(timestamp);
                event.setDetails(details);
                yield event;
            }
            case BOOKING -> {
                BookingEvent event = new BookingEvent();
                event.setBookingId(longValue(params.get("bookingId")));
                event.setAction(action);
                event.setTimestamp(timestamp);
                event.setDetails(details);
                yield event;
            }
            case TICKET -> {
                TicketEvent event = new TicketEvent();
                event.setTicketId(longValue(params.get("ticketId")));
                event.setAction(action);
                event.setTimestamp(timestamp);
                event.setDetails(details);
                yield event;
            }
            case PAYMENT_AUDIT -> {
                PaymentAuditEvent event = new PaymentAuditEvent();
                event.setSaleId(longValue(params.get("saleId")));
                event.setAction(action);
                event.setTimestamp(timestamp);
                event.setMethod(stringValue(params.get("method")));
                event.setAmount(doubleValue(params.get("amount")));
                event.setDetails(details);
                yield event;
            }
        };
    }

    private Map<String, Object> copyDetails(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> details = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    details.put(key, entry.getValue());
                }
            }
            return details;
        }
        return new LinkedHashMap<>();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
