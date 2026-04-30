package com.team06.eventticketing.booking.repository;

import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceGraphRepository {

    private final Neo4jClient neo4jClient;

    public AttendanceGraphRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
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
}
