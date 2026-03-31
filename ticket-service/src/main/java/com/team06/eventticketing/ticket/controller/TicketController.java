package com.team06.eventticketing.ticket.controller;

import com.team06.eventticketing.ticket.dto.NearbyTicketResponseDTO;
import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.service.TicketService;
import java.time.LocalDate;
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
    public Ticket getTicketById(@PathVariable Long id) { return ticketService.getTicketById(id); }

    @GetMapping("/metadata/search")
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
    public Ticket updateTicket(@PathVariable Long id, @RequestBody Ticket ticket) {
        return ticketService.updateTicket(id, ticket);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTicket(@PathVariable Long id) { ticketService.deleteTicket(id); }

    @DeleteMapping("/purge")
    public PurgeTicketsResponseDTO purgeTickets(@RequestParam long olderThanDays) {
        return ticketService.purgeTickets(olderThanDays);
    }

    @GetMapping("/booking/{bookingId}/latest")
    public Ticket getLatestTicketForBooking(@PathVariable Long bookingId) {
        return ticketService.getLatestTicketForBooking(bookingId);
    }

    @PostMapping("/booking/{bookingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket issueTicketWithMetadata(@PathVariable Long bookingId, @RequestBody Map<String, Object> request) {
        String attendeeName = (String) request.get("attendeeName");
        String ticketCode = (String) request.get("ticketCode");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");

        if (attendeeName == null || attendeeName.isBlank() || ticketCode == null || ticketCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attendeeName and ticketCode are required");
        }

        return ticketService.issueTicketWithMetadata(bookingId, attendeeName, ticketCode, metadata);
    }

    @GetMapping("/history")
    public List<Ticket> getTicketHistory(
            @RequestParam(name = "startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "status", required = false) TicketStatus status) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        return ticketService.getTicketsHistory(startDateTime, endExclusive, status);
    }

    @GetMapping("/unused-upcoming")
    public List<UnusedTicketDTO> getUnusedUpcomingTickets() {
        return ticketService.getUnusedUpcomingTickets();
    }

    @GetMapping("/nearby")
    public List<NearbyTicketResponseDTO> findTicketsNearVenue(
            @RequestParam(name = "lat") double latitude,
            @RequestParam(name = "lon") double longitude,
            @RequestParam double radiusKm
    ) {
        return ticketService.findTicketsNearVenue(latitude, longitude, radiusKm);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> batchIssue(@RequestBody Map<String, Object> request) {
        Long bookingId = Long.valueOf(request.get("bookingId").toString());
        List<Map<String, Object>> ticketMaps = (List<Map<String, Object>>) request.get("tickets");
        List<Ticket> tickets = ticketMaps.stream().map(m -> {
            Ticket t = new Ticket();
            t.setAttendeeName(m.get("attendeeName").toString());
            t.setTicketCode(m.get("ticketCode").toString());
            if (m.get("metadata") != null) {
                t.setMetadata((Map<String, Object>) m.get("metadata"));
            }
            return t;
        }).toList();
        return ticketService.batchIssue(bookingId, tickets);
    }
}
