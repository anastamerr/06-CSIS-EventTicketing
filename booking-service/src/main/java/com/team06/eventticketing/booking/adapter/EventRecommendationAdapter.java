package com.team06.eventticketing.booking.adapter;

import com.team06.eventticketing.booking.dto.EventRecommendationCandidate;
import com.team06.eventticketing.booking.dto.EventRecommendationDTO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

@Component
public class EventRecommendationAdapter {

    public EventRecommendationCandidate candidateFromRecord(Record record) {
        return new EventRecommendationCandidate(
                record.get("eventId").asLong(),
                record.get("score").asLong());
    }

    public EventRecommendationDTO dtoFromResultSet(ResultSet resultSet, Map<Long, Long> scores) throws SQLException {
        Long eventId = resultSet.getLong("id");
        return new EventRecommendationDTO(
                eventId,
                resultSet.getString("name"),
                resultSet.getString("category"),
                resultSet.getObject("event_date", LocalDateTime.class),
                scores.getOrDefault(eventId, 0L));
    }
}
