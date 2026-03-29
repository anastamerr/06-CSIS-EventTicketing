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
}
