package com.team06.eventticketing.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.ticket.dto.NearbyTicketResponseDTO;
import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.dto.TicketAnalyticsDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketAnalyticsEvent;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.NearbyTicketProjection;
import com.team06.eventticketing.ticket.repository.TicketAnalyticsEventRepository;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketService {

    private static final String ANALYTICS_CACHE_PREFIX = "ticket:analytics:";
    private static final long ANALYTICS_CACHE_TTL_SECONDS = 600L;

    private final TicketRepository ticketRepository;
    private final TicketAnalyticsEventRepository analyticsEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository,
                         TicketAnalyticsEventRepository analyticsEventRepository,
                         StringRedisTemplate redisTemplate,
                         Clock clock) {
        this.ticketRepository = ticketRepository;
        this.analyticsEventRepository = analyticsEventRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.clock = clock;
    }

    public List<Ticket> getAllTickets() { return ticketRepository.findAll(); }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    public Ticket createTicket(Ticket ticket) {
        ticket.setStatus(TicketStatus.VALID);
        Ticket saved = ticketRepository.save(ticket);
        invalidateAnalyticsCache();
        return saved;
    }

    @Transactional
    public Ticket issueTicketWithMetadata(Long bookingId, String attendeeName, String ticketCode, Map<String, Object> metadata) {
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
        ticket.setStatus(TicketStatus.VALID);
        ticket.setIssuedAt(LocalDateTime.now(clock));
        ticket.setMetadata(metadata != null ? metadata : Map.of());

        Ticket saved = ticketRepository.save(ticket);
        invalidateAnalyticsCache();
        return saved;
    }

    public List<Ticket> getTicketsHistory(LocalDateTime startDateTime, LocalDateTime endDateTime, TicketStatus status) {
        if (startDateTime.isAfter(endDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        String statusValue = status == null ? null : status.name();
        return ticketRepository.findByIssuedAtBetweenAndStatus(startDateTime, endDateTime, statusValue);
    }

    public TicketAnalyticsDTO getTicketAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        logAnalyticsViewed(startDate, endDate);

        String cacheKey = buildCacheKey(startDate, endDate);
        TicketAnalyticsDTO cached = readAnalyticsCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.of(23, 59, 59, 999_000_000));

        long totalIssued = ticketRepository.countByIssuedAtBetween(startDateTime, endDateTime);
        List<Object[]> statusCounts = ticketRepository.countStatusByIssuedAtBetween(startDateTime, endDateTime);
        Map<String, Long> countsMap = countsByStatus(statusCounts);

        long usedCount = countsMap.getOrDefault(TicketStatus.USED.name(), 0L);
        long validCount = countsMap.getOrDefault(TicketStatus.VALID.name(), 0L);
        long expiredCount = countsMap.getOrDefault(TicketStatus.EXPIRED.name(), 0L);
        long cancelledCount = countsMap.getOrDefault(TicketStatus.CANCELLED.name(), 0L);
        double attendanceRate = totalIssued == 0 ? 0.0 : usedCount / (double) totalIssued;
        Map<String, Long> ticketsByStatus = totalIssued == 0 ? Map.of() : countsMap.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        TicketAnalyticsDTO result = TicketAnalyticsDTO.builder()
                .totalIssued(totalIssued)
                .usedCount(usedCount)
                .validCount(validCount)
                .expiredCount(expiredCount)
                .cancelledCount(cancelledCount)
                .attendanceRate(attendanceRate)
                .ticketsByStatus(ticketsByStatus)
                .build();

        writeAnalyticsCache(cacheKey, result);
        return result;
    }

    private String buildCacheKey(LocalDate startDate, LocalDate endDate) {
        return ANALYTICS_CACHE_PREFIX + startDate + ":" + endDate;
    }

    private TicketAnalyticsDTO readAnalyticsCache(String cacheKey) {
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TicketAnalyticsDTO.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeAnalyticsCache(String cacheKey, TicketAnalyticsDTO analyticsDTO) {
        try {
            String json = objectMapper.writeValueAsString(analyticsDTO);
            redisTemplate.opsForValue().set(cacheKey, json, java.time.Duration.ofSeconds(ANALYTICS_CACHE_TTL_SECONDS));
        } catch (Exception ignored) {
        }
    }

    private Map<String, Long> countsByStatus(List<Object[]> statusCounts) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : statusCounts) {
            if (row.length >= 2 && row[0] != null && row[1] != null) {
                String status = row[0].toString();
                Long count = Long.parseLong(row[1].toString());
                result.put(status, count);
            }
        }
        return result;
    }

    private void logAnalyticsViewed(LocalDate startDate, LocalDate endDate) {
        TicketAnalyticsEvent event = new TicketAnalyticsEvent(
                "ANALYTICS_VIEWED",
                getCurrentUserEmail(),
                startDate,
                endDate,
                LocalDateTime.now(clock),
                Map.of("startDate", startDate.toString(), "endDate", endDate.toString())
        );
        analyticsEventRepository.save(event);
    }

    private String getCurrentUserEmail() {
        var context = SecurityContextHolder.getContext();
        if (context == null || context.getAuthentication() == null || context.getAuthentication().getPrincipal() == null) {
            return "anonymous";
        }
        return context.getAuthentication().getPrincipal().toString();
    }

    private void invalidateAnalyticsCache() {
        try {
            var keys = redisTemplate.keys(ANALYTICS_CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    public Ticket updateTicket(Long id, Ticket ticket) {
        Ticket existingTicket = getTicketById(id);
        existingTicket.setBookingId(ticket.getBookingId());
        existingTicket.setAttendeeName(ticket.getAttendeeName());
        existingTicket.setTicketCode(ticket.getTicketCode());
        existingTicket.setStatus(ticket.getStatus());
        existingTicket.setIssuedAt(ticket.getIssuedAt());
        existingTicket.setMetadata(ticket.getMetadata());
        Ticket updated = ticketRepository.save(existingTicket);
        invalidateAnalyticsCache();
        return updated;
    }

    public void deleteTicket(Long id) {
        getTicketById(id);
        ticketRepository.deleteById(id);
        invalidateAnalyticsCache();
    }

    public Ticket getLatestTicketForBooking(Long bookingId) {
        if (!ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return ticketRepository.findTopByBookingIdOrderByIssuedAtDesc(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for this booking"));
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
        invalidateAnalyticsCache();
        return new PurgeTicketsResponseDTO(deletedCount);
    }

    @Transactional
    public Map<String, Object> batchIssue(Long bookingId, List<Ticket> tickets) {
        if (bookingId == null || !ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
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
        ticketRepository.saveAll(tickets);
        invalidateAnalyticsCache();

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

    private NearbyTicketResponseDTO toNearbyTicketResponse(NearbyTicketProjection projection) {
        NearbyTicketResponseDTO response = new NearbyTicketResponseDTO();
        response.setTicketId(projection.getTicketId());
        response.setAttendeeName(projection.getAttendeeName());
        response.setBookingId(projection.getBookingId());
        response.setEventName(projection.getEventName());
        response.setEventLat(projection.getEventLat());
        response.setEventLon(projection.getEventLon());
        response.setDistanceKm(projection.getDistanceKm());
        return response;
    }
}
