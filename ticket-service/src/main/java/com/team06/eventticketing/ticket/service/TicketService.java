package com.team06.eventticketing.ticket.service;

import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.contracts.events.TicketCancelledEvent;
import com.team06.eventticketing.contracts.events.TicketStatusChangedEvent;
import com.team06.eventticketing.ticket.adapter.CassandraRowAdapter;
import com.team06.eventticketing.ticket.adapter.EventAttendanceSummaryAdapter;
import com.team06.eventticketing.ticket.dto.EventAttendanceSummaryDTO;
import com.team06.eventticketing.ticket.dto.EventTicketSummaryDTO;
import com.team06.eventticketing.ticket.dto.NearbyTicketResponseDTO;
import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.dto.TicketAnalyticsDTO;
import com.team06.eventticketing.ticket.dto.TicketScanDTO;
import com.team06.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import com.team06.eventticketing.ticket.scan.TicketScanEvent;
import com.team06.eventticketing.ticket.scan.TicketScanEventRepository;
import com.team06.eventticketing.ticket.scan.TicketScanRequest;
import com.team06.eventticketing.ticket.client.EventServiceClient;
import com.team06.eventticketing.ticket.client.EventServiceClient.EventResponse;
import com.team06.eventticketing.ticket.dto.VenueCoordsDTO;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team06.eventticketing.ticket.client.BookingServiceClient;
import feign.FeignException;
import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.ticket.messaging.TicketEventPublisher;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final Clock clock;
    private final EventAttendanceSummaryAdapter eventAttendanceSummaryAdapter;
    private final CassandraRowAdapter cassandraRowAdapter;
    private final TicketScanEventRepository ticketScanEventRepository;
    private final RedisCacheService redisCacheService;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
    private final AtomicLong lastScanEpochMillis = new AtomicLong();
    private final BookingServiceClient bookingServiceClient;
    private final EventServiceClient eventServiceClient;
    private final TicketEventPublisher ticketEventPublisher;

    @Autowired
    public TicketService(
            TicketRepository ticketRepository,
            Clock clock,
            EventAttendanceSummaryAdapter eventAttendanceSummaryAdapter,
            CassandraRowAdapter cassandraRowAdapter,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory,
            TicketScanEventRepository ticketScanEventRepository,
            RedisCacheService redisCacheService,
            BookingServiceClient bookingServiceClient,
            EventServiceClient eventServiceClient,
            TicketEventPublisher ticketEventPublisher
    ) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
        this.eventAttendanceSummaryAdapter = eventAttendanceSummaryAdapter;
        this.cassandraRowAdapter = cassandraRowAdapter;
        this.ticketScanEventRepository = ticketScanEventRepository;
        this.redisCacheService = redisCacheService;
        this.bookingServiceClient = bookingServiceClient;
        this.eventServiceClient = eventServiceClient;
        this.ticketEventPublisher = ticketEventPublisher;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public TicketService(TicketRepository ticketRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
        this.eventAttendanceSummaryAdapter = new EventAttendanceSummaryAdapter();
        this.cassandraRowAdapter = new CassandraRowAdapter();
        this.ticketScanEventRepository = null;
        this.redisCacheService = null;
        this.bookingServiceClient = null;
        this.eventServiceClient = null;
        this.ticketEventPublisher = null;
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
        publishTicketIssued(saved);
        notifyObservers("TICKET_ISSUED", Map.of(
                "ticketId", saved.getId(),
                "details", buildTicketDetails(saved)));
        return saved;
    }

    @Transactional
    public Ticket issueTicketWithMetadata(Long bookingId, String attendeeName, String ticketCode, Map<String, Object> metadata) {
        Ticket ticket = buildTicketForIssue(bookingId, attendeeName, ticketCode, metadata);
        ticket.setStatus(TicketStatus.VALID);
        Ticket saved = ticketRepository.save(ticket);
        publishTicketIssued(saved);
        return saved;
    }

    private Ticket buildTicketForIssue(Long bookingId, String attendeeName, String ticketCode, Map<String, Object> metadata) {
        BookingDTO booking = fetchBooking(bookingId);  // throws 404 if missing

        if (ticketRepository.findByTicketCode(ticketCode).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket code already exists");
        }

        Ticket ticket = new Ticket();
        ticket.setBookingId(bookingId);
        ticket.setEventId(booking.eventId());  // denormalize for F8/F9
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

    @Transactional(readOnly = true)
    @CachedFeature(service = "ticket-service", featureId = "S4-F10", ttlSeconds = 600)
    public TicketAnalyticsDTO getTicketAnalytics(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.of(23, 59, 59, 999_000_000));
        List<Object[]> rows = ticketRepository.findAnalyticsByIssuedAtBetween(startDateTime, endDateTime);
        Object[] row = rows.isEmpty() ? new Object[]{0L, 0L, 0L, 0L, 0L} : rows.getFirst();

        long totalIssued = toLong(row[0]);
        long usedCount = toLong(row[1]);
        long validCount = toLong(row[2]);
        long expiredCount = toLong(row[3]);
        long cancelledCount = toLong(row[4]);
        double attendanceRate = totalIssued == 0 ? 0.0 : (double) usedCount / totalIssued;

        Map<String, Long> ticketsByStatus = new LinkedHashMap<>();
        if (totalIssued > 0) {
            ticketsByStatus.put(TicketStatus.VALID.name(), validCount);
            ticketsByStatus.put(TicketStatus.USED.name(), usedCount);
            ticketsByStatus.put(TicketStatus.EXPIRED.name(), expiredCount);
            ticketsByStatus.put(TicketStatus.CANCELLED.name(), cancelledCount);
        }

        return TicketAnalyticsDTO.builder()
                .totalIssued(totalIssued)
                .usedCount(usedCount)
                .validCount(validCount)
                .expiredCount(expiredCount)
                .cancelledCount(cancelledCount)
                .attendanceRate(attendanceRate)
                .ticketsByStatus(ticketsByStatus)
                .build();
    }

    public void logAnalyticsViewed(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        notifyObservers("ANALYTICS_VIEWED", Map.of(
                "ticketId", 0L,
                "timestamp", LocalDateTime.now(clock),
                "details", Map.of(
                        "featureId", "S4-F10",
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString())));
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
        fetchBooking(bookingId);
        return ticketRepository.findTopByBookingIdOrderByIssuedAtDesc(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for this booking"));
    }

    private BookingDTO fetchBooking(Long bookingId) {
        if (bookingId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (bookingServiceClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Booking service client is not configured");
        }
        try {
            BookingDTO booking = bookingServiceClient.getBooking(bookingId);
            if (booking == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
            }
            return booking;
        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        } catch (FeignException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to verify booking: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public int getUsedTicketCountForBooking(Long bookingId) {
        if (bookingId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookingId is required");
        }
        return Math.toIntExact(ticketRepository.countByBookingIdAndStatus(bookingId, TicketStatus.USED));
    }

    @Transactional(readOnly = true)
    public EventAttendanceSummaryDTO getEventAttendanceSummary(Long eventId) {
        List<Ticket> tickets = ticketRepository.findByEventId(eventId);
        long totalTickets = tickets.size();
        if (totalTickets == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for this event");
        }
        long usedTickets = tickets.stream().filter(ticket -> ticket.getStatus() == TicketStatus.USED).count();
        long validTickets = tickets.stream().filter(ticket -> ticket.getStatus() == TicketStatus.VALID).count();
        LocalDateTime lastCheckIn = tickets.stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.USED)
                .map(ticket -> ticket.getMetadata() == null ? null : ticket.getMetadata().get("checkInTime"))
                .filter(value -> value != null && !value.toString().isBlank())
                .map(value -> LocalDateTime.parse(value.toString()))
                .max(Comparator.naturalOrder())
                .orElse(null);

        return eventAttendanceSummaryAdapter.adapt(eventId, new Object[]{totalTickets, usedTickets, validTickets, lastCheckIn});
    }

    @Transactional(readOnly = true)
    public EventTicketSummaryDTO getEventTicketSummary(Long eventId) {
        List<Object[]> rows = ticketRepository.findAttendanceSummaryByEventId(eventId);
        if (rows.isEmpty()) {
            return new EventTicketSummaryDTO(0L, 0L);
        }
        Object[] row = rows.getFirst();
        long totalTicketsSold = toLong(row[0]);
        long usedCount = toLong(row[1]);
        return new EventTicketSummaryDTO(totalTicketsSold, usedCount);
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
        return ticketRepository.findByStatusAndEventIdIsNotNull(TicketStatus.VALID).stream()
                .map(this::toUnusedTicketDTO)
                .filter(dto -> dto != null && dto.getEventDate() != null)
                .sorted(Comparator.comparing(UnusedTicketDTO::getEventDate).thenComparing(UnusedTicketDTO::getTicketId))
                .toList();
    }

    public List<NearbyTicketResponseDTO> findTicketsNearVenue(double latitude, double longitude, double radiusKm) {
        validateGeoParameters(latitude, longitude, radiusKm);
        return ticketRepository.findByStatusAndEventIdIsNotNull(TicketStatus.VALID).stream()
                .map(ticket -> toNearbyTicketResponse(ticket, latitude, longitude))
                .filter(dto -> dto != null && dto.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(NearbyTicketResponseDTO::getDistanceKm).thenComparing(NearbyTicketResponseDTO::getTicketId))
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
        BookingDTO booking = fetchBooking(bookingId);

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
            ticket.setEventId(booking.eventId());
            ticket.setStatus(TicketStatus.VALID);
        }
        List<Ticket> savedTickets = ticketRepository.saveAll(tickets);
        savedTickets.forEach(this::publishTicketIssued);
        notifyObservers("BATCH_ISSUED", Map.of(
                "ticketId", savedTickets.isEmpty() ? 0L : savedTickets.getFirst().getId(),
                "details", Map.of("count", savedTickets.size(), "bookingId", bookingId)));

        return Map.of("count", savedTickets.size());
    }

    @Transactional
    public TicketScanEvent recordScanEvent(Long ticketId, TicketScanRequest request) {
        Ticket ticket = getTicketById(ticketId);
        TicketScanRequest safeRequest = request == null ? new TicketScanRequest() : request;

        TicketScanEvent scanEvent = new TicketScanEvent();
        scanEvent.setTicketId(ticketId);
        scanEvent.setTimestamp(nextScanTimestamp());
        scanEvent.setScanType(defaultScanType(safeRequest.getScanType()));
        scanEvent.setAttendeeName(ticket.getAttendeeName());
        scanEvent.setGate(safeRequest.getGate());
        scanEvent.setSection(safeRequest.getSection());
        scanEvent.setSeatNumber(safeRequest.getSeatNumber());
        scanEvent.setNotes(safeRequest.getNotes());

        TicketScanEvent saved = ticketScanEventRepository.save(scanEvent);
        notifyObservers("TRACKING_RECORDED", Map.of(
                "ticketId", ticketId,
                "details", Map.of(
                        "scanType", scanEvent.getScanType(),
                        "gate", safeText(safeRequest.getGate()),
                        "section", safeText(safeRequest.getSection()),
                        "seatNumber", safeText(safeRequest.getSeatNumber()))));
        if (redisCacheService != null) {
            redisCacheService.deleteByPattern("ticket-service::S4-F12::*");
            redisCacheService.deleteByPattern("ticket-service::S4-F10::*");
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TicketScanDTO> getTicketScanHistory(Long ticketId, LocalDateTime startTime, LocalDateTime endTime) {
        getTicketById(ticketId);
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must not be after endTime");
        }
        Instant startInstant = startTime == null ? null : startTime.atZone(ZoneOffset.UTC).toInstant();
        Instant endInstant = endTime == null ? null : endTime.atZone(ZoneOffset.UTC).toInstant();
        var rows = (startInstant == null && endInstant == null)
                ? ticketScanEventRepository.findByTicketId(ticketId)
                : ticketScanEventRepository.findByTicketIdAndRange(ticketId, startInstant, endInstant);
        return rows.stream()
                .map(cassandraRowAdapter::adaptScan)
                .toList();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String defaultScanType(String value) {
        return value == null || value.isBlank() ? "CHECKED_IN" : value;
    }

    private Instant nextScanTimestamp() {
        long nowMillis = Instant.now(clock).toEpochMilli();
        long uniqueMillis = lastScanEpochMillis.updateAndGet(previous -> Math.max(nowMillis, previous + 1));
        return Instant.ofEpochMilli(uniqueMillis);
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

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
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

    private UnusedTicketDTO toUnusedTicketDTO(Ticket ticket) {
        EventResponse event = fetchEvent(ticket.getEventId());
        if (event == null || event.status() == null || !"UPCOMING".equalsIgnoreCase(event.status())) {
            return null;
        }
        return UnusedTicketDTO.builder()
                .ticketId(ticket.getId())
                .attendeeName(ticket.getAttendeeName())
                .ticketCode(ticket.getTicketCode())
                .bookingId(ticket.getBookingId())
                .eventName(event.name())
                .eventDate(event.eventDate())
                .build();
    }

    private NearbyTicketResponseDTO toNearbyTicketResponse(Ticket ticket, double latitude, double longitude) {
        EventResponse event = fetchEvent(ticket.getEventId());
        VenueCoordsDTO coords = fetchVenueCoords(ticket.getEventId());
        if (event == null || coords == null || coords.venueLat() == null || coords.venueLon() == null) {
            return null;
        }
        double distanceKm = distanceKm(latitude, longitude, coords.venueLat(), coords.venueLon());
        return NearbyTicketResponseDTO.builder()
                .ticketId(ticket.getId())
                .attendeeName(ticket.getAttendeeName())
                .ticketCode(ticket.getTicketCode())
                .bookingId(ticket.getBookingId())
                .eventName(event.name())
                .eventLat(coords.venueLat())
                .eventLon(coords.venueLon())
                .distanceKm(distanceKm)
                .build();
    }

    private EventResponse fetchEvent(Long eventId) {
        if (eventId == null || eventServiceClient == null) {
            return null;
        }
        try {
            return eventServiceClient.getEvent(eventId);
        } catch (FeignException.NotFound ex) {
            return null;
        }
    }

    private VenueCoordsDTO fetchVenueCoords(Long eventId) {
        if (eventId == null || eventServiceClient == null) {
            return null;
        }
        try {
            return eventServiceClient.getEventVenueCoords(eventId);
        } catch (FeignException.NotFound ex) {
            return null;
        }
    }

    private double distanceKm(double latitude, double longitude, double eventLat, double eventLon) {
        return Math.sqrt(Math.pow(eventLat - latitude, 2) + Math.pow(eventLon - longitude, 2)) * 111.0;
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

    @Transactional(readOnly = true)
    public long getUsedTicketCountForBooking(Long bookingId) {
        return ticketRepository.countUsedByBookingId(bookingId);
    }

    @Transactional
    public int captureEventIdForBooking(Long bookingId, Long eventId) {
        requireSagaField(bookingId, "bookingId");
        requireSagaField(eventId, "eventId");
        int updated = ticketRepository.backfillEventIdByBookingId(bookingId, eventId);
        if (updated > 0) {
            invalidateTicketCaches(ticketRepository.findByBookingId(bookingId));
        }
        return updated;
    }

    @Transactional
    public int publishStatusChangedAuditSignals(Long bookingId) {
        requireSagaField(bookingId, "bookingId");
        List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
        int published = 0;
        for (Ticket ticket : tickets) {
            if (ticket.getId() == null || ticket.getStatus() == null) {
                continue;
            }
            if (ticketEventPublisher != null) {
                ticketEventPublisher.publishTicketStatusChanged(
                        new TicketStatusChangedEvent(
                                ticket.getId(),
                                ticket.getBookingId(),
                                ticket.getStatus().name()),
                        ticket.getEventId());
            }
            published++;
        }
        return published;
    }

    @Transactional
    public int cancelTicketsForBooking(Long bookingId) {
        requireSagaField(bookingId, "bookingId");
        List<Ticket> cancellableTickets = ticketRepository.findByBookingId(bookingId).stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.VALID)
                .map(ticket -> {
                    ticket.setStatus(TicketStatus.CANCELLED);
                    return ticket;
                })
                .toList();
        if (cancellableTickets.isEmpty()) {
            return 0;
        }

        List<Ticket> cancelledTickets = ticketRepository.saveAll(cancellableTickets);
        invalidateTicketCaches(cancelledTickets);
        for (Ticket ticket : cancelledTickets) {
            if (ticketEventPublisher != null && ticket.getId() != null) {
                ticketEventPublisher.publishTicketCancelled(
                        new TicketCancelledEvent(ticket.getId(), ticket.getBookingId()),
                        ticket.getEventId());
            }
        }
        return cancelledTickets.size();
    }

    private void publishTicketIssued(Ticket ticket) {
        if (ticketEventPublisher == null) {
            return;  // test-only constructor path; no broker wired
        }
        ticketEventPublisher.publishTicketIssued(
                new TicketEventPublisher.TicketIssuedEvent(
                        ticket.getId(),
                        ticket.getBookingId(),
                        ticket.getEventId(),
                        ticket.getTicketCode()
                )
        );
    }

    private void invalidateTicketCaches(List<Ticket> tickets) {
        if (redisCacheService == null) {
            return;
        }
        for (Ticket ticket : tickets) {
            if (ticket.getId() != null) {
                redisCacheService.delete("ticket-service::ticket::" + ticket.getId());
            }
        }
        redisCacheService.deleteByPattern("ticket-service::S4-F*::*");
    }

    private void requireSagaField(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
