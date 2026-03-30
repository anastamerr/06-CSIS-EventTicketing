package com.team06.eventticketing.ticket.controller;

import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
