package com.team06.eventticketing.ticket.service;

import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.ticket.adapter.EventAttendanceSummaryAdapter;
import com.team06.eventticketing.ticket.dto.NearbyTicketResponseDTO;
import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.NearbyTicketProjection;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import com.team06.eventticketing.ticket.repository.UnusedTicketProjection;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team06.eventticketing.ticket.dto.EventAttendanceSummaryDTO;

import java.util.List;
import java.util.Map;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final Clock clock;
    private final EventAttendanceSummaryAdapter eventAttendanceSummaryAdapter;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public TicketService(
            TicketRepository ticketRepository,
            Clock clock,
            EventAttendanceSummaryAdapter eventAttendanceSummaryAdapter,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
        this.eventAttendanceSummaryAdapter = eventAttendanceSummaryAdapter;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public TicketService(TicketRepository ticketRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
        this.eventAttendanceSummaryAdapter = new EventAttendanceSummaryAdapter();
    }

    public List<Ticket> getAllTickets() { return ticketRepository.findAll(); }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    public Ticket createTicket(Ticket ticket) {
        if (ticket.getStatus() == null) {
            ticket.setStatus(TicketStatus.VALID);
        }
        if (ticket.getMetadata() == null) {
            ticket.setMetadata(new LinkedHashMap<>());
        }
        Ticket saved = ticketRepository.save(ticket);
        notifyObservers("TICKET_CREATED", Map.of(
                "ticketId", saved.getId(),
                "details", buildTicketDetails(saved)));
        return saved;
    }

    @Transactional
    public Ticket issueTicketWithMetadata(
            Long bookingId,
            String attendeeName,
            String ticketCode,
            String status,
            Map<String, Object> metadata
    ) {
        Ticket ticket = buildTicketForIssue(bookingId, attendeeName, ticketCode, metadata);
        ticket.setStatus(resolveTicketStatus(status));
        Ticket saved = ticketRepository.save(ticket);
        notifyObservers("TICKET_ISSUED", Map.of(
                "ticketId", saved.getId(),
                "details", buildTicketDetails(saved)));
        return saved;
    }

    @Transactional
    public Ticket issueTicketWithMetadata(Long bookingId, String attendeeName, String ticketCode, Map<String, Object> metadata) {
        Ticket ticket = buildTicketForIssue(bookingId, attendeeName, ticketCode, metadata);
        ticket.setStatus(TicketStatus.VALID);
        return ticketRepository.save(ticket);
    }

    private Ticket buildTicketForIssue(Long bookingId, String attendeeName, String ticketCode, Map<String, Object> metadata) {
        if (bookingId == null || !ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        if (ticketRepository.findByTicketCode(ticketCode).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket code already exists");
        }

        Ticket ticket = new Ticket();
        ticket.setBookingId(bookingId);
        ticket.setAttendeeName(attendeeName);
        ticket.setTicketCode(ticketCode);
        ticket.setIssuedAt(LocalDateTime.now(clock));
        ticket.setMetadata(metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata));

        return ticket;
    }

    private TicketStatus resolveTicketStatus(String status) {
        if (status == null || status.isBlank()) {
            return TicketStatus.VALID;
        }
        try {
            return TicketStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return TicketStatus.VALID;
        }
    }

    public List<Ticket> getTicketsHistory(LocalDateTime startDateTime, LocalDateTime endExclusive, TicketStatus status) {
        if (!startDateTime.isBefore(endExclusive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        String statusValue = status == null ? null : status.name();
        return ticketRepository.findByIssuedAtBetweenAndStatus(startDateTime, endExclusive, statusValue);
    }

    public Ticket updateTicket(Long id, Ticket ticket) {
        Ticket existingTicket = getTicketById(id);
        if (ticket.getBookingId() != null) {
            existingTicket.setBookingId(ticket.getBookingId());
        }
        if (ticket.getAttendeeName() != null) {
            existingTicket.setAttendeeName(ticket.getAttendeeName());
        }
        if (ticket.getTicketCode() != null) {
            existingTicket.setTicketCode(ticket.getTicketCode());
        }
        if (ticket.getStatus() != null) {
            existingTicket.setStatus(ticket.getStatus());
        }
        if (ticket.getIssuedAt() != null) {
            existingTicket.setIssuedAt(ticket.getIssuedAt());
        }
        if (ticket.getMetadata() != null && !ticket.getMetadata().isEmpty()) {
            existingTicket.setMetadata(new LinkedHashMap<>(ticket.getMetadata()));
        }
        Ticket saved = ticketRepository.save(existingTicket);
        notifyObservers("TICKET_UPDATED", Map.of(
                "ticketId", saved.getId(),
                "details", buildTicketDetails(saved)));
        return saved;
    }

    public LocalDateTime parseFlexibleStart(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            return java.time.LocalDate.parse(value).atStartOfDay();
        }
    }

    public LocalDateTime parseFlexibleEndExclusive(String value) {
        try {
            return LocalDateTime.parse(value).plusSeconds(1);
        } catch (java.time.format.DateTimeParseException ignored) {
            return java.time.LocalDate.parse(value).plusDays(1).atStartOfDay();
        }
    }

    public void deleteTicket(Long id) {
        getTicketById(id);
        notifyObservers("TICKET_DELETED", Map.of(
                "ticketId", id,
                "details", buildTicketDetails(getTicketById(id))));
        ticketRepository.deleteById(id);
    }

    public Ticket getLatestTicketForBooking(Long bookingId) {
        if (!ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return ticketRepository.findTopByBookingIdOrderByIssuedAtDesc(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for this booking"));
    }

    @Transactional(readOnly = true)
    public EventAttendanceSummaryDTO getEventAttendanceSummary(Long eventId) {
        List<Object[]> rows = ticketRepository.findAttendanceSummaryByEventId(eventId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for this event");
        }

        Object[] row = rows.getFirst();
        long totalTickets = toLong(row[0]);
        if (totalTickets == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for this event");
        }

        long usedTickets = toLong(row[1]);
        long validTickets = toLong(row[2]);
        double attendanceRate = (usedTickets * 100.0) / totalTickets;
        LocalDateTime lastCheckIn = toLocalDateTime(row[3]);

        return eventAttendanceSummaryAdapter.adapt(eventId, row);
    }

    @Transactional(readOnly = true)
    public List<Ticket> searchTicketsByMetadata(String key, String operator, String value) {
        String normalizedKey = requireText(key, "key");
        String normalizedOperator = requireText(operator, "operator").toLowerCase();
        String normalizedValue = requireText(value, "value");

        return switch (normalizedOperator) {
            case "eq" -> ticketRepository.findByMetadataFieldEquals(normalizedKey, normalizedValue);
            case "gt" -> {
                requireNumericValue(normalizedValue);
                yield ticketRepository.findByMetadataFieldGreaterThan(normalizedKey, normalizedValue);
            }
            case "lt" -> {
                requireNumericValue(normalizedValue);
                yield ticketRepository.findByMetadataFieldLessThan(normalizedKey, normalizedValue);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "operator must be one of eq, gt, lt");
        };
    }

    @Transactional(readOnly = true)
    public List<UnusedTicketDTO> getUnusedUpcomingTickets() {
        return ticketRepository.findUnusedTicketsForUpcomingEvents()
                .stream()
                .map(this::mapToUnusedTicketDTO)
                .toList();
    }

    public List<NearbyTicketResponseDTO> findTicketsNearVenue(double latitude, double longitude, double radiusKm) {
        validateGeoParameters(latitude, longitude, radiusKm);
        return ticketRepository.findTicketsNearVenue(latitude, longitude, radiusKm).stream()
                .map(this::toNearbyTicketResponse)
                .toList();
    }

    @Transactional
    public PurgeTicketsResponseDTO purgeTickets(long olderThanDays) {
        if (olderThanDays < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "olderThanDays must not be negative");
        }

        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(olderThanDays);
        long purgeableCount = ticketRepository.countPurgeableTickets(cutoff);
        if (purgeableCount == 0) {
            return new PurgeTicketsResponseDTO(0);
        }

        int deletedCount = ticketRepository.deletePurgeableTickets(cutoff);
        notifyObservers("OLD_DATA_PURGED", Map.of(
                "ticketId", 0L,
                "details", Map.of("deletedCount", deletedCount, "olderThanDays", olderThanDays)));
        return new PurgeTicketsResponseDTO(deletedCount);
    }

    @Transactional
    public Map<String, Object> batchIssue(Long bookingId, List<Ticket> tickets) {
        if (bookingId == null || !ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        if (tickets == null || tickets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tickets is required");
        }

        List<String> codes = tickets.stream().map(Ticket::getTicketCode).toList();
        long distinctCount = codes.stream().distinct().count();
        if (distinctCount != codes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate ticket codes in batch");
        }

        for (String code : codes) {
            if (ticketRepository.findByTicketCode(code).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket code already exists: " + code);
            }
        }

        for (Ticket ticket : tickets) {
            ticket.setBookingId(bookingId);
            ticket.setStatus(TicketStatus.VALID);
        }
        List<Ticket> savedTickets = ticketRepository.saveAll(tickets);
        notifyObservers("BATCH_ISSUED", Map.of(
                "ticketId", savedTickets.isEmpty() ? 0L : savedTickets.getFirst().getId(),
                "details", Map.of("count", savedTickets.size(), "bookingId", bookingId)));

        return Map.of("count", tickets.size());
    }

    private void validateGeoParameters(double latitude, double longitude, double radiusKm) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude must be between -90 and 90");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "longitude must be between -180 and 180");
        }
        if (radiusKm <= 0.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm must be greater than zero");
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private void requireNumericValue(String value) {
        try {
            new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value must be numeric for gt/lt");
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString());
    }

    private UnusedTicketDTO mapToUnusedTicketDTO(UnusedTicketProjection p) {
        return UnusedTicketDTO.builder()
                .ticketId(p.getTicketId())
                .attendeeName(p.getAttendeeName())
                .ticketCode(p.getTicketCode())
                .bookingId(p.getBookingId())
                .eventName(p.getEventName())
                .eventDate(p.getEventDate())
                .build();
    }

    private NearbyTicketResponseDTO toNearbyTicketResponse(NearbyTicketProjection projection) {
        return NearbyTicketResponseDTO.builder()
                .ticketId(projection.getTicketId())
                .attendeeName(projection.getAttendeeName())
                .ticketCode(projection.getTicketCode())
                .bookingId(projection.getBookingId())
                .eventName(projection.getEventName())
                .eventLat(projection.getEventLat())
                .eventLon(projection.getEventLon())
                .distanceKm(projection.getDistanceKm())
                .build();
    }

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String action, Object payload) {
        observers.forEach(observer -> observer.onEvent(action, payload));
    }

    private Map<String, Object> buildTicketDetails(Ticket ticket) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bookingId", ticket.getBookingId());
        details.put("ticketCode", ticket.getTicketCode());
        details.put("status", ticket.getStatus() == null ? null : ticket.getStatus().name());
        return details;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.TICKET, "ticket_events"));
        }
    }
}
