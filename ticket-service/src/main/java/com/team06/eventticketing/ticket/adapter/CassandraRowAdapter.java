package com.team06.eventticketing.ticket.adapter;

import com.datastax.oss.driver.api.core.cql.Row;
import com.team06.eventticketing.ticket.dto.TicketScanDTO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class CassandraRowAdapter {

    public TicketScanDTO adapt(Row row) {
        if (row == null) {
            return new TicketScanDTO(null, null, null, null, null, null, null);
        }
        LocalDateTime timestamp = row.getInstant("timestamp") == null
                ? null
                : LocalDateTime.ofInstant(row.getInstant("timestamp"), ZoneOffset.UTC);
        return new TicketScanDTO(
                timestamp,
                row.getString("scan_type"),
                row.getString("attendee_name"),
                row.getString("gate"),
                row.getString("section"),
                row.getString("seat_number"),
                row.getString("notes"));
    }

    public TicketScanDTO adaptScan(Row row) {
        return adapt(row);
    }
}
