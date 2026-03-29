package com.team06.eventticketing.ticket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Captor
    private ArgumentCaptor<List<Ticket>> ticketsCaptor;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository);
    }

    @Test
    void batchIssueRejectsNonexistentBooking() {
        when(ticketRepository.existsBookingById(55L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.batchIssue(55L, List.of(ticket("A", "TIX-1"))));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(ticketRepository, never()).saveAll(anyList());
    }

    @Test
    void batchIssueRejectsDuplicateCodesInsideBatch() {
        List<Ticket> tickets = List.of(ticket("A", "TIX-1"), ticket("B", "TIX-1"));
        when(ticketRepository.existsBookingById(55L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.batchIssue(55L, tickets));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).saveAll(anyList());
    }

    @Test
    void batchIssueAssignsBookingAndValidStatusAndReturnsCount() {
        Ticket firstTicket = ticket("A", "TIX-1");
        Ticket secondTicket = ticket("B", "TIX-2");
        List<Ticket> tickets = List.of(firstTicket, secondTicket);

        when(ticketRepository.existsBookingById(55L)).thenReturn(true);
        when(ticketRepository.findByTicketCode("TIX-1")).thenReturn(Optional.empty());
        when(ticketRepository.findByTicketCode("TIX-2")).thenReturn(Optional.empty());

        Map<String, Object> response = ticketService.batchIssue(55L, tickets);

        verify(ticketRepository).saveAll(ticketsCaptor.capture());
        List<Ticket> savedTickets = ticketsCaptor.getValue();
        assertEquals(2, savedTickets.size());
        assertEquals(55L, firstTicket.getBookingId());
        assertEquals(TicketStatus.VALID, firstTicket.getStatus());
        assertEquals(55L, secondTicket.getBookingId());
        assertEquals(TicketStatus.VALID, secondTicket.getStatus());
        assertEquals(2, response.get("count"));
    }

    @Test
    void getLatestTicketThrowsNotFoundForNonexistentBooking() {
        when(ticketRepository.existsBookingById(99L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.getLatestTicketForBooking(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getLatestTicketThrowsNotFoundWhenNoTicketsExist() {
        when(ticketRepository.existsBookingById(55L)).thenReturn(true);
        when(ticketRepository.findTopByBookingIdOrderByIssuedAtDesc(55L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.getLatestTicketForBooking(55L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getLatestTicketReturnsLatestByIssuedAt() {
        Ticket latest = ticket("Ahmed", "TIX-3");

        when(ticketRepository.existsBookingById(55L)).thenReturn(true);
        when(ticketRepository.findTopByBookingIdOrderByIssuedAtDesc(55L)).thenReturn(Optional.of(latest));

        Ticket result = ticketService.getLatestTicketForBooking(55L);

        assertEquals(latest, result);
    }

    private Ticket ticket(String attendeeName, String ticketCode) {
        Ticket ticket = new Ticket();
        ticket.setAttendeeName(attendeeName);
        ticket.setTicketCode(ticketCode);
        ticket.setMetadata(Map.of());
        return ticket;
    }
}
