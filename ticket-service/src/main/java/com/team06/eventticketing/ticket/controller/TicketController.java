package com.team06.eventticketing.ticket.controller;

import com.team06.eventticketing.common.cache.CachedDetail;
import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.ticket.dto.NearbyTicketResponseDTO;
import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.scan.TicketScanEvent;
import com.team06.eventticketing.ticket.scan.TicketScanRequest;
import com.team06.eventticketing.ticket.service.TicketService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.team06.eventticketing.ticket.dto.EventAttendanceSummaryDTO;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }


    @GetMapping
    public List<Ticket> getAllTickets() { return ticketService.getAllTickets(); }

    @GetMapping("/{id}")
    @CachedDetail(service = "ticket-service", entity = "ticket", key = "#id", ttlSeconds = 900)
    public Ticket getTicketById(@PathVariable Long id) { return ticketService.getTicketById(id); }

    @GetMapping("/metadata/search")
    @CachedFeature(service = "ticket-service", featureId = "S4-F1", ttlSeconds = 300)
    public List<Ticket> searchTicketsByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value
    ) {
        return ticketService.searchTicketsByMetadata(key, operator, value);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket createTicket(@RequestBody Ticket ticket) { return ticketService.createTicket(ticket); }

    @PutMapping("/{id}")
    @InvalidateServiceCaches(
            service = "ticket-service",
            featurePrefix = "S4-",
            detailKeys = {"'ticket-service::ticket::' + #id"})
    public Ticket updateTicket(@PathVariable Long id, @RequestBody Ticket ticket) {
        return ticketService.updateTicket(id, ticket);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @InvalidateServiceCaches(
            service = "ticket-service",
            featurePrefix = "S4-",
            detailKeys = {"'ticket-service::ticket::' + #id"})
    public void deleteTicket(@PathVariable Long id) { ticketService.deleteTicket(id); }

    @DeleteMapping("/purge")
    @InvalidateServiceCaches(service = "ticket-service", featurePrefix = "S4-")
    public PurgeTicketsResponseDTO purgeTickets(@RequestParam long olderThanDays) {
        return ticketService.purgeTickets(olderThanDays);
    }

    @GetMapping("/booking/{bookingId}/latest")
    @CachedFeature(service = "ticket-service", featureId = "S4-F5", ttlSeconds = 300)
    public Ticket getLatestTicketForBooking(@PathVariable Long bookingId) {
        return ticketService.getLatestTicketForBooking(bookingId);
    }

    @GetMapping("/event/{eventId}/summary")
    @CachedFeature(service = "ticket-service", featureId = "S4-F3", ttlSeconds = 600)
    public EventAttendanceSummaryDTO getEventAttendanceSummary(@PathVariable Long eventId) {
        return ticketService.getEventAttendanceSummary(eventId);
    }

    @PostMapping("/booking/{bookingId}")
    @ResponseStatus(HttpStatus.CREATED)
    @InvalidateServiceCaches(service = "ticket-service", featurePrefix = "S4-")
    public Ticket issueTicketWithMetadata(@PathVariable Long bookingId, @RequestBody Map<String, Object> request) {
        String attendeeName = (String) request.get("attendeeName");
        String ticketCode = (String) request.get("ticketCode");
        String status = request.get("status") == null ? null : request.get("status").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

        if (attendeeName == null || attendeeName.isBlank() || ticketCode == null || ticketCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attendeeName and ticketCode are required");
        }

        return ticketService.issueTicketWithMetadata(bookingId, attendeeName, ticketCode, status, metadata);
    }

    @GetMapping("/history")
    @CachedFeature(service = "ticket-service", featureId = "S4-F6", ttlSeconds = 600)
    public List<Ticket> getTicketHistory(
            @RequestParam(name = "startDate") String startDate,
            @RequestParam(name = "endDate") String endDate,
            @RequestParam(name = "status", required = false) TicketStatus status
    ) {
        try {
            LocalDateTime startDateTime = ticketService.parseFlexibleStart(startDate);
            LocalDateTime endExclusive = ticketService.parseFlexibleEndExclusive(endDate);
            return ticketService.getTicketsHistory(startDateTime, endExclusive, status);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format");
        }
    }

    @GetMapping("/unused-upcoming")
    @CachedFeature(service = "ticket-service", featureId = "S4-F8", ttlSeconds = 900)
    public List<UnusedTicketDTO> getUnusedUpcomingTickets() {
        return ticketService.getUnusedUpcomingTickets();
    }

    @GetMapping("/nearby")
    @CachedFeature(service = "ticket-service", featureId = "S4-F9", ttlSeconds = 600)
    public List<NearbyTicketResponseDTO> findTicketsNearVenue(
            @RequestParam(name = "lat") double latitude,
            @RequestParam(name = "lon") double longitude,
            @RequestParam double radiusKm
    ) {
        return ticketService.findTicketsNearVenue(latitude, longitude, radiusKm);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @InvalidateServiceCaches(service = "ticket-service", featurePrefix = "S4-")
    public Map<String, Object> batchIssue(@RequestBody Map<String, Object> request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        Long bookingId = requireLong(request.get("bookingId"), "bookingId");
        List<?> ticketMaps = requireTicketList(request.get("tickets"));
        List<Ticket> tickets = ticketMaps.stream().map(this::toBatchTicket).toList();

        return ticketService.batchIssue(bookingId, tickets);
    }

    @PostMapping("/{id}/scan")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketScanEvent recordScanEvent(@PathVariable Long id, @RequestBody TicketScanRequest request) {
        return ticketService.recordScanEvent(id, request);
    }

    private List<?> requireTicketList(Object value) {
        if (!(value instanceof List<?> ticketMaps) || ticketMaps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tickets is required");
        }
        return ticketMaps;
    }

    private Ticket toBatchTicket(Object value) {
        if (!(value instanceof Map<?, ?> rawTicket)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each ticket must be an object");
        }

        Ticket ticket = new Ticket();
        ticket.setAttendeeName(requireText(rawTicket.get("attendeeName"), "attendeeName"));
        ticket.setTicketCode(requireText(rawTicket.get("ticketCode"), "ticketCode"));

        Object metadata = rawTicket.get("metadata");
        if (metadata != null) {
            ticket.setMetadata(castMetadata(metadata));
        }

        return ticket;
    }

    private Map<String, Object> castMetadata(Object value) {
        if (!(value instanceof Map<?, ?> rawMetadata)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "metadata must be an object");
        }

        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMetadata.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "metadata keys must be strings");
            }
            metadata.put(key, entry.getValue());
        }
        return metadata;
    }

    private Long requireLong(Object value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be a number");
        }
    }

    private String requireText(Object value, String fieldName) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return text;
    }
}
