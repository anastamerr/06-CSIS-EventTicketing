package com.team06.eventticketing.booking.repository;

import com.team06.eventticketing.booking.adapter.EventRecommendationAdapter;
import com.team06.eventticketing.booking.dto.EventRecommendationDTO;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRecommendationLookupRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EventRecommendationAdapter recommendationAdapter;

    public BookingRecommendationLookupRepository(
            JdbcTemplate jdbcTemplate,
            EventRecommendationAdapter recommendationAdapter) {
        this.jdbcTemplate = jdbcTemplate;
        this.recommendationAdapter = recommendationAdapter;
    }

    public boolean userExists(Long userId) {
        String sql = "SELECT EXISTS (SELECT 1 FROM users WHERE id = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, userId);
        return Boolean.TRUE.equals(exists);
    }

    public List<EventRecommendationDTO> findEventsByIds(List<Long> eventIds, Map<Long, Long> scores) {
        if (eventIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", eventIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT id, name, category::text AS category, event_date
                FROM events
                WHERE id IN (%s)
                """.formatted(placeholders);

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> recommendationAdapter.dtoFromResultSet(resultSet, scores),
                eventIds.toArray());
    }
}
