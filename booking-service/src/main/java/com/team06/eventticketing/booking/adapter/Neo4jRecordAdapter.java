package com.team06.eventticketing.booking.adapter;

import java.util.LinkedHashMap;
import java.util.Map;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

@Component
public class Neo4jRecordAdapter {

    public Map<String, Object> adapt(Record record) {
        if (record == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : record.keys()) {
            values.put(key, record.get(key).asObject());
        }
        return values;
    }
}
