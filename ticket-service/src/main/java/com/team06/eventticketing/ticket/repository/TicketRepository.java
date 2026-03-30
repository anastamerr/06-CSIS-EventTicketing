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
            WITH ticket_distances AS (
                SELECT
                    t.id AS ticketId,
                    t.booking_id AS bookingId,
                    b.event_id AS eventId,
                    e.name AS eventName,
                    e.venue AS venue,
                    t.attendee_name AS attendeeName,
                    t.ticket_code AS ticketCode,
                    t.status AS ticketStatus,
                    t.issued_at AS issuedAt,
                    CAST(e.details ->> 'venueLat' AS double precision) AS venueLatitude,
                    CAST(e.details ->> 'venueLon' AS double precision) AS venueLongitude,
                    6371.0 * ACOS(
                        LEAST(
                            1.0,
                            GREATEST(
                                -1.0,
                                COS(RADIANS(:latitude)) * COS(RADIANS(CAST(e.details ->> 'venueLat' AS double precision))) *
                                COS(RADIANS(CAST(e.details ->> 'venueLon' AS double precision)) - RADIANS(:longitude)) +
                                SIN(RADIANS(:latitude)) * SIN(RADIANS(CAST(e.details ->> 'venueLat' AS double precision)))
                            )
                        )
                    ) AS distanceKm
                FROM tickets t
                JOIN bookings b ON b.id = t.booking_id
                JOIN events e ON e.id = b.event_id
                WHERE jsonb_exists(e.details, 'venueLat')
                  AND jsonb_exists(e.details, 'venueLon')
            )
            SELECT *
            FROM ticket_distances
            WHERE distanceKm <= :radiusKm
            ORDER BY distanceKm ASC, ticketId ASC
            """, nativeQuery = true)
    List<NearbyTicketProjection> findTicketsNearVenue(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm
    );
}
