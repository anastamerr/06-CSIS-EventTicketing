package com.team06.eventticketing.ticket.repository;

import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByBookingId(Long bookingId);

    Optional<Ticket> findByTicketCode(String ticketCode);

    List<Ticket> findByStatus(TicketStatus status);

    Optional<Ticket> findTopByBookingIdOrderByIssuedAtDesc(Long bookingId);

    @Query(value = """
            SELECT *
            FROM tickets
            WHERE issued_at >= :startDate
              AND issued_at < :endExclusive
              AND (:status IS NULL OR status = :status)
            ORDER BY issued_at ASC, id ASC
            """, nativeQuery = true)
    List<Ticket> findByIssuedAtBetweenAndStatus(
            @Param("startDate") LocalDateTime startDate,
            @Param("endExclusive") LocalDateTime endExclusive,
            @Param("status") String status);

    @Query(value = """
            SELECT COUNT(*)
            FROM tickets
            WHERE status IN ('EXPIRED', 'CANCELLED')
              AND issued_at < :cutoff
            """, nativeQuery = true)
    long countPurgeableTickets(@Param("cutoff") LocalDateTime cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM tickets
            WHERE status IN ('EXPIRED', 'CANCELLED')
              AND issued_at < :cutoff
            """, nativeQuery = true)
    int deletePurgeableTickets(@Param("cutoff") LocalDateTime cutoff);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM bookings WHERE id = :bookingId)", nativeQuery = true)
    boolean existsBookingById(@Param("bookingId") Long bookingId);

    @Query(value = """
            SELECT
                t.id            AS ticketId,
                t.attendee_name AS attendeeName,
                t.ticket_code   AS ticketCode,
                t.booking_id    AS bookingId,
                e.name          AS eventName,
                e.event_date    AS eventDate
            FROM tickets t
            JOIN bookings b ON b.id = t.booking_id
            JOIN events e   ON e.id = b.event_id
            WHERE t.status = 'VALID'
              AND e.status = 'UPCOMING'
            ORDER BY e.event_date ASC, t.id ASC
            """, nativeQuery = true)
    List<UnusedTicketProjection> findUnusedTicketsForUpcomingEvents();

    @Query(value = """
            SELECT
                t.id AS ticketId,
                t.attendee_name AS attendeeName,
                t.booking_id AS bookingId,
                e.name AS eventName,
                CAST(e.details ->> 'venueLat' AS double precision) AS eventLat,
                CAST(e.details ->> 'venueLon' AS double precision) AS eventLon,
                SQRT(
                    POWER(CAST(e.details ->> 'venueLat' AS double precision) - :latitude, 2) +
                    POWER(CAST(e.details ->> 'venueLon' AS double precision) - :longitude, 2)
                ) * 111.0 AS distanceKm
            FROM tickets t
            JOIN bookings b ON b.id = t.booking_id
            JOIN events e ON e.id = b.event_id
            WHERE t.status = 'VALID'
              AND jsonb_exists(e.details, 'venueLat')
              AND jsonb_exists(e.details, 'venueLon')
              AND SQRT(
                    POWER(CAST(e.details ->> 'venueLat' AS double precision) - :latitude, 2) +
                    POWER(CAST(e.details ->> 'venueLon' AS double precision) - :longitude, 2)
                ) * 111.0 <= :radiusKm
            ORDER BY distanceKm ASC, ticketId ASC
            """, nativeQuery = true)
    List<NearbyTicketProjection> findTicketsNearVenue(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm
    );

    @Query(value = """
            SELECT *
            FROM tickets
            WHERE metadata ->> :key = :value
            ORDER BY id ASC
            """, nativeQuery = true)
    List<Ticket> findByMetadataFieldEquals(
            @Param("key") String key,
            @Param("value") String value
    );

    @Query(value = """
            SELECT *
            FROM tickets
            WHERE (metadata ->> :key) ~ '^-?[0-9]+(\\.[0-9]+)?$'
              AND CAST(metadata ->> :key AS numeric) > CAST(:value AS numeric)
            ORDER BY id ASC
            """, nativeQuery = true)
    List<Ticket> findByMetadataFieldGreaterThan(
            @Param("key") String key,
            @Param("value") String value
    );

    @Query(value = """
            SELECT *
            FROM tickets
            WHERE (metadata ->> :key) ~ '^-?[0-9]+(\\.[0-9]+)?$'
              AND CAST(metadata ->> :key AS numeric) < CAST(:value AS numeric)
            ORDER BY id ASC
            """, nativeQuery = true)
    List<Ticket> findByMetadataFieldLessThan(
            @Param("key") String key,
            @Param("value") String value
    );

    @Query(value = """
        SELECT
            COUNT(t.id) AS totalTickets,
            COALESCE(SUM(CASE WHEN t.status = 'USED' THEN 1 ELSE 0 END), 0) AS usedTickets,
            COALESCE(SUM(CASE WHEN t.status = 'VALID' THEN 1 ELSE 0 END), 0) AS validTickets,
            MAX(
                CASE
                    WHEN t.status = 'USED'
                    THEN NULLIF(t.metadata ->> 'checkInTime', '')::timestamp
                END
            ) AS lastCheckIn
        FROM tickets t
        JOIN bookings b ON b.id = t.booking_id
        WHERE b.event_id = :eventId
        """, nativeQuery = true)
    List<Object[]> findAttendanceSummaryByEventId(@Param("eventId") Long eventId);
}
