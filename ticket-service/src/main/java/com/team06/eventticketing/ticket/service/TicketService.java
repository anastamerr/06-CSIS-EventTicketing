package com.team06.eventticketing.ticket.service;

import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public List<Ticket> getAllTickets() { return ticketRepository.findAll(); }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    public Ticket createTicket(Ticket ticket) {
        ticket.setStatus(TicketStatus.VALID);
        return ticketRepository.save(ticket);
    }

    public void deleteTicket(Long id) {
        getTicketById(id);
        ticketRepository.deleteById(id);
    }

    public Ticket getLatestTicketForBooking(Long bookingId) {
        if (!ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return ticketRepository.findTopByBookingIdOrderByIssuedAtDesc(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for this booking"));
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

        return Map.of("count", tickets.size());
    }
}
