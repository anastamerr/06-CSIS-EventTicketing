package com.team06.eventticketing.ticket.adapter;

import com.datastax.oss.driver.api.core.cql.Row;
import com.team06.eventticketing.ticket.dto.TicketScanDTO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CassandraRowAdapter {

    public Map<String, Object> adapt(Row row) {
        if (row == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        row.getColumnDefinitions().forEach(definition -> {
            String columnName = definition.getName().asInternal();
            values.put(columnName, row.getObject(columnName));
        });
        return values;
    }

    public TicketScanDTO adaptScan(Row row) {
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
}
