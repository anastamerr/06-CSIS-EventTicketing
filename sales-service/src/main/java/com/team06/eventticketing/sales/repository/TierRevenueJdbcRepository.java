package com.team06.eventticketing.sales.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TierRevenueJdbcRepository {

    public TierRevenueJdbcRepository(JdbcTemplate jdbcTemplate) {
    }

    public List<Object[]> aggregateByTier(LocalDateTime startInclusive, LocalDateTime endInclusive) {
        return List.of();
    }
}
