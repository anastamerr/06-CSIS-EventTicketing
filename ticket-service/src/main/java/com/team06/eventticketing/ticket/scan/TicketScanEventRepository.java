package com.team06.eventticketing.ticket.scan;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class TicketScanEventRepository {

    private static final Logger log = LoggerFactory.getLogger(TicketScanEventRepository.class);
    private static final String KEYSPACE = "eventticketingks";
    private static final String TABLE = KEYSPACE + ".ticket_scan_events";

    private final CqlSession cqlSession;

    public TicketScanEventRepository(CqlSession cqlSession) {
        this.cqlSession = cqlSession;
    }

    @PostConstruct
    void ensureTable() {
        tryEnsureTable();
    }

    public TicketScanEvent save(TicketScanEvent event) {
        tryEnsureTable();
        cqlSession.execute(String.format("""
                INSERT INTO %s (
                    ticket_id, timestamp, scan_type, attendee_name, gate, section, seat_number, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, TABLE),
                event.getTicketId(),
                event.getTimestamp(),
                event.getScanType(),
                event.getAttendeeName(),
                event.getGate(),
                event.getSection(),
                event.getSeatNumber(),
                event.getNotes());
        return event;
    }

    public List<Row> findByTicketId(Long ticketId) {
        tryEnsureTable();
        return cqlSession.execute(String.format("""
                SELECT ticket_id, timestamp, scan_type, attendee_name, gate, section, seat_number, notes
                FROM %s
                WHERE ticket_id = ?
                ORDER BY timestamp DESC
                """, TABLE), ticketId).all();
    }

    public List<Row> findByTicketIdAndRange(Long ticketId, Instant startTime, Instant endTime) {
        tryEnsureTable();
        StringBuilder cql = new StringBuilder(String.format("""
                SELECT ticket_id, timestamp, scan_type, attendee_name, gate, section, seat_number, notes
                FROM %s
                WHERE ticket_id = ?
                """, TABLE));
        List<Object> params = new ArrayList<>();
        params.add(ticketId);
        if (startTime != null) {
            cql.append(" AND timestamp >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            cql.append(" AND timestamp <= ?");
            params.add(endTime);
        }
        cql.append(" ORDER BY timestamp DESC");
        return cqlSession.execute(cql.toString(), params.toArray()).all();
    }

    private void tryEnsureTable() {
        try {
            cqlSession.execute("""
                    CREATE KEYSPACE IF NOT EXISTS eventticketingks
                    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
                    """);
            cqlSession.execute("""
                    CREATE TABLE IF NOT EXISTS eventticketingks.ticket_scan_events (
                        ticket_id bigint,
                        timestamp timestamp,
                        scan_type text,
                        attendee_name text,
                        gate text,
                        section text,
                        seat_number text,
                        notes text,
                        PRIMARY KEY (ticket_id, timestamp)
                    ) WITH CLUSTERING ORDER BY (timestamp DESC)
                    """);
        } catch (RuntimeException exception) {
            log.warn("Could not ensure Cassandra ticket_scan_events table yet", exception);
        }
    }
}
