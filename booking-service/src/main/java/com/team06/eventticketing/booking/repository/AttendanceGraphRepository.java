package com.team06.eventticketing.booking.repository;

import com.team06.eventticketing.booking.adapter.EventRecommendationAdapter;
import com.team06.eventticketing.booking.dto.EventRecommendationCandidate;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceGraphRepository {

    private final Neo4jClient neo4jClient;
    private final EventRecommendationAdapter recommendationAdapter;

    public AttendanceGraphRepository(Neo4jClient neo4jClient, EventRecommendationAdapter recommendationAdapter) {
        this.neo4jClient = neo4jClient;
        this.recommendationAdapter = recommendationAdapter;
    }

    public boolean alreadyRecorded(Long userId, Long eventId, Long bookingId) {
        return neo4jClient.query("""
                        MATCH (u:UserNode {userId: $userId})-[r:ATTENDED]->(e:EventNode {eventId: $eventId})
                        WHERE $bookingId IN coalesce(r.recorded_booking_ids, [])
                        RETURN count(r) > 0 AS alreadyRecorded
                        """)
                .bind(userId).to("userId")
                .bind(eventId).to("eventId")
                .bind(bookingId).to("bookingId")
                .fetchAs(Boolean.class)
                .mappedBy((typeSystem, record) -> record.get("alreadyRecorded").asBoolean())
                .one()
                .orElse(false);
    }

    public Map<String, Object> recordAttendance(
            Long userId,
            String userName,
            Long eventId,
            String eventName,
            String eventCategory,
            Long bookingId) {
        return neo4jClient.query("""
                        MERGE (u:UserNode {userId: $userId})
                        ON CREATE SET u.name = $userName
                        ON MATCH SET u.name = $userName
                        MERGE (e:EventNode {eventId: $eventId})
                        ON CREATE SET e.name = $eventName, e.category = $eventCategory
                        ON MATCH SET e.name = $eventName, e.category = $eventCategory
                        MERGE (u)-[r:ATTENDED]->(e)
                        ON CREATE SET
                            r.attendanceCount = 1,
                            r.lastAttendedDate = datetime(),
                            r.recorded_booking_ids = [$bookingId]
                        ON MATCH SET
                            r.attendanceCount = coalesce(r.attendanceCount, 0) + 1,
                            r.lastAttendedDate = datetime(),
                            r.recorded_booking_ids = coalesce(r.recorded_booking_ids, []) + $bookingId
                        RETURN {
                            userId: u.userId,
                            eventId: e.eventId,
                            attendanceCount: r.attendanceCount,
                            recordedBookingIds: r.recorded_booking_ids
                        } AS result
                        """)
                .bind(userId).to("userId")
                .bind(userName == null ? "" : userName).to("userName")
                .bind(eventId).to("eventId")
                .bind(eventName == null ? "" : eventName).to("eventName")
                .bind(eventCategory == null ? "" : eventCategory).to("eventCategory")
                .bind(bookingId).to("bookingId")
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> record.get("result").asMap())
                .one()
                .orElse(Map.of());
    }

    public List<EventRecommendationCandidate> findRecommendations(Long userId, int limit) {
        if (limit == 0) {
            return List.of();
        }

        return neo4jClient.query("""
                        MATCH (target:UserNode {userId: $userId})-[:ATTENDED]->(shared:EventNode)
                              <-[:ATTENDED]-(similar:UserNode)
                        WHERE similar.userId <> $userId
                        MATCH (similar)-[:ATTENDED]->(recommended:EventNode)
                        WHERE NOT (target)-[:ATTENDED]->(recommended)
                        RETURN recommended.eventId AS eventId, count(DISTINCT similar) AS score
                        ORDER BY score DESC, eventId ASC
                        LIMIT $limit
                        """)
                .bind(userId).to("userId")
                .bind(limit).to("limit")
                .fetchAs(EventRecommendationCandidate.class)
                .mappedBy((typeSystem, record) -> recommendationAdapter.candidateFromRecord(record))
                .all()
                .stream()
                .toList();
    }
}
