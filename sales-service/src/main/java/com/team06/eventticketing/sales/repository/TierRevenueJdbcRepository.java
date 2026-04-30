package com.team06.eventticketing.sales.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TierRevenueJdbcRepository {

    private static final String AGGREGATE_BY_TIER_SQL = """
            SELECT
                COALESCE(NULLIF(bi.metadata->>'ticketTier', ''), 'UNSPECIFIED') AS tier,
                COALESCE(SUM(bi.unit_price * bi.quantity), 0) AS total_revenue,
                COUNT(DISTINCT ts.id) AS sale_count,
                COALESCE(SUM(bi.quantity), 0) AS tickets_sold
            FROM ticket_sales ts
            JOIN bookings b ON b.id = ts.booking_id
            JOIN booking_items bi ON bi.booking_id = b.id
            WHERE ts.created_at >= ?
              AND ts.created_at <= ?
              AND ts.status = 'COMPLETED'
            GROUP BY 1
            ORDER BY 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public TierRevenueJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Object[]> aggregateByTier(LocalDateTime startInclusive, LocalDateTime endInclusive) {
        return jdbcTemplate.query(
                AGGREGATE_BY_TIER_SQL,
                (resultSet, rowNum) -> new Object[]{
                        resultSet.getString(1),
                        resultSet.getDouble(2),
                        resultSet.getLong(3),
                        resultSet.getLong(4)
                },
                startInclusive,
                endInclusive);
    }
}
