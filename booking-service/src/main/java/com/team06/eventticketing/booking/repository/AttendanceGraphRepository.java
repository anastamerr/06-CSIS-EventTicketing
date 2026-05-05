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
                        MATCH (u:User {id: $userId})-[r:ATTENDED]->(e:Event {id: $eventId})
                        WHERE $bookingId IN coalesce(r.recordedBookingIds, [])
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
                        MERGE (u:User {id: $userId})
                        ON CREATE SET u.name = $userName, u.userId = $userId
                        ON MATCH SET u.name = $userName, u.userId = $userId
                        MERGE (e:Event {id: $eventId})
                        ON CREATE SET e.name = $eventName, e.category = $eventCategory, e.eventId = $eventId
                        ON MATCH SET e.name = $eventName, e.category = $eventCategory, e.eventId = $eventId
                        MERGE (u)-[r:ATTENDED]->(e)
                        ON CREATE SET
                            r.attendanceCount = 1,
                            r.lastAttendedDate = datetime(),
                            r.recordedBookingIds = [$bookingId]
                        ON MATCH SET
                            r.attendanceCount = CASE
                                WHEN $bookingId IN coalesce(r.recordedBookingIds, []) THEN coalesce(r.attendanceCount, 0)
                                ELSE coalesce(r.attendanceCount, 0) + 1
                            END,
                            r.lastAttendedDate = CASE
                                WHEN $bookingId IN coalesce(r.recordedBookingIds, []) THEN r.lastAttendedDate
                                ELSE datetime()
                            END,
                            r.recordedBookingIds = CASE
                                WHEN $bookingId IN coalesce(r.recordedBookingIds, []) THEN coalesce(r.recordedBookingIds, [])
                                ELSE coalesce(r.recordedBookingIds, []) + [$bookingId]
                            END
                        RETURN {
                            userId: u.id,
                            eventId: e.id,
                            attendanceCount: r.attendanceCount,
                            lastAttendedDate: r.lastAttendedDate,
                            recordedBookingIds: r.recordedBookingIds
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
                        MATCH (target:User {id: $userId})-[:ATTENDED]->(shared:Event)
                              <-[:ATTENDED]-(similar:User)
                        WHERE similar.id <> $userId
                        MATCH (similar)-[:ATTENDED]->(recommended:Event)
                        WHERE NOT (target)-[:ATTENDED]->(recommended)
                        RETURN recommended.id AS eventId, count(DISTINCT similar) AS score
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
