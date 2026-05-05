package com.team06.eventticketing.booking.adapter;

import com.team06.eventticketing.booking.dto.EventRecommendationCandidate;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

@Component
public class Neo4jRecordAdapter {

    public EventRecommendationCandidate adapt(Record record) {
        if (record == null) {
            return new EventRecommendationCandidate(null, 0L);
        }
        Long eventId = record.containsKey("eventId") && !record.get("eventId").isNull()
                ? record.get("eventId").asLong()
                : null;
        long score = record.containsKey("score") && !record.get("score").isNull()
                ? record.get("score").asLong()
                : 0L;
        return new EventRecommendationCandidate(eventId, score);
    }
}
