package com.team06.eventticketing.contracts.messaging;

public final class EventTicketingMessagingContracts {

    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String TICKET_EVENTS_EXCHANGE = "ticket.events";

    public static final String BOOKING_PLACED_ROUTING_KEY = "booking.placed";
    public static final String BOOKING_COMPLETED_ROUTING_KEY = "booking.completed";
    public static final String BOOKING_CANCELLED_ROUTING_KEY = "booking.cancelled";

    public static final String PAYMENT_INITIATED_ROUTING_KEY = "payment.initiated";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";
    public static final String PAYMENT_REFUNDED_ROUTING_KEY = "payment.refunded";

    public static final String TICKET_ISSUED_ROUTING_KEY = "ticket.issued";

    public static final String BOOKING_SAGA_FEEDBACK_QUEUE = "booking.saga-feedback";
    public static final String BOOKING_SAGA_FEEDBACK_DLQ = "booking.saga-feedback.dlq";
    public static final String BOOKING_SAGA_FEEDBACK_DLX = "booking.saga-feedback.dlx";
    public static final String BOOKING_SAGA_FEEDBACK_DLQ_ROUTING_KEY = "booking.saga-feedback.dead";

    private EventTicketingMessagingContracts() {
    }
}
