package com.team06.eventticketing.ticket.adapter;

import com.team06.eventticketing.ticket.dto.TicketScanDTO;
import java.time.LocalDateTime;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public TicketScanDTO adapt(Document document) {
        if (document == null) {
            return new TicketScanDTO(null, null, null, null, null, null, null);
        }
        return new TicketScanDTO(
                asLocalDateTime(document.get("timestamp")),
                document.getString("scanType"),
                document.getString("attendeeName"),
                document.getString("gate"),
                document.getString("section"),
                document.getString("seatNumber"),
                document.getString("notes"));
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime timestamp) {
            return timestamp;
        }
        if (value instanceof String text && !text.isBlank()) {
            return LocalDateTime.parse(text);
        }
        return null;
    }
}
