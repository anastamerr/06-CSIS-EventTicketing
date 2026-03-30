package com.team06.eventticketing.ticket.controller;

import com.team06.eventticketing.ticket.dto.NearbyTicketResponseDTO;
import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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
            @RequestParam(name = "startDate") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(name = "endDate") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(name = "status", required = false) TicketStatus status) {
        java.time.LocalDateTime startDateTime = startDate.atStartOfDay();
        java.time.LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        return ticketService.getTicketsHistory(startDateTime, endDateTime, status);
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
