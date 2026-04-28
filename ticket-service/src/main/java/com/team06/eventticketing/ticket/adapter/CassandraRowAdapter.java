package com.team06.eventticketing.ticket.adapter;

import com.datastax.oss.driver.api.core.cql.Row;
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
}
