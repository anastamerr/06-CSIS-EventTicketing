package com.team06.eventticketing.ticket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.ticket.dto.NearbyTicketResponseDTO;
import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.NearbyTicketProjection;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import com.team06.eventticketing.ticket.repository.UnusedTicketProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-29T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TicketRepository ticketRepository;

    @Captor
    private ArgumentCaptor<List<Ticket>> ticketsCaptor;

    @Captor
    private ArgumentCaptor<Ticket> ticketCaptor;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, FIXED_CLOCK);
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
    void issueTicketWithMetadataRejectsMissingBooking() {
        when(ticketRepository.existsBookingById(404L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.issueTicketWithMetadata(404L, "Ahmed", "TIX-404", Map.of("seatNumber", "A12")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void issueTicketWithMetadataCreatesValidTicketWithNowAndMetadata() {
        when(ticketRepository.existsBookingById(55L)).thenReturn(true);
        when(ticketRepository.findByTicketCode("TIX-2026-001")).thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Ticket result = ticketService.issueTicketWithMetadata(
                55L,
                "Ahmed",
                "TIX-2026-001",
                Map.of("seatNumber", "A12", "section", "VIP"));

        verify(ticketRepository).save(ticketCaptor.capture());
        Ticket savedTicket = ticketCaptor.getValue();
        assertEquals(55L, savedTicket.getBookingId());
        assertEquals("Ahmed", savedTicket.getAttendeeName());
        assertEquals("TIX-2026-001", savedTicket.getTicketCode());
        assertEquals(TicketStatus.VALID, savedTicket.getStatus());
        assertEquals(LocalDateTime.of(2026, 3, 29, 12, 0), savedTicket.getIssuedAt());
        assertEquals("A12", savedTicket.getMetadata().get("seatNumber"));
        assertEquals(savedTicket, result);
    }

    @Test
    void getTicketsHistoryRejectsInvalidDateRange() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.getTicketsHistory(
                        LocalDateTime.of(2026, 4, 1, 0, 0),
                        LocalDateTime.of(2026, 4, 1, 0, 0),
                        TicketStatus.VALID));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).findByIssuedAtBetweenAndStatus(any(), any(), any());
    }

    @Test
    void getTicketsHistoryDelegatesToRepositoryWithOptionalStatus() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime endExclusive = LocalDateTime.of(2026, 4, 1, 0, 0);
        List<Ticket> expectedTickets = List.of(ticket("Ahmed", "TIX-1"), ticket("Sara", "TIX-2"));

        when(ticketRepository.findByIssuedAtBetweenAndStatus(start, endExclusive, "VALID")).thenReturn(expectedTickets);

        List<Ticket> actualTickets = ticketService.getTicketsHistory(start, endExclusive, TicketStatus.VALID);

        assertIterableEquals(expectedTickets, actualTickets);
    }

    @Test
    void purgeTicketsRejectsNegativeOlderThanDays() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.purgeTickets(-1));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).countPurgeableTickets(any());
        verify(ticketRepository, never()).deletePurgeableTickets(any());
    }

    @Test
    void purgeTicketsReturnsZeroWhenNoTicketsMatch() {
        when(ticketRepository.countPurgeableTickets(any())).thenReturn(0L);

        PurgeTicketsResponseDTO response = ticketService.purgeTickets(30);

        assertEquals(0L, response.deletedCount());
        verify(ticketRepository).countPurgeableTickets(any());
        verify(ticketRepository, never()).deletePurgeableTickets(any());
    }

    @Test
    void purgeTicketsDeletesEligibleTicketsAndReturnsDeletedCount() {
        when(ticketRepository.countPurgeableTickets(any())).thenReturn(4L);
        when(ticketRepository.deletePurgeableTickets(any())).thenReturn(4);

        PurgeTicketsResponseDTO response = ticketService.purgeTickets(30);

        assertEquals(4L, response.deletedCount());
        verify(ticketRepository).countPurgeableTickets(any());
        verify(ticketRepository).deletePurgeableTickets(any());
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

    @Test
    void getUnusedUpcomingTicketsMapsRepositoryResults() {
        UnusedTicketProjection projection = new UnusedTicketProjection() {
            @Override
            public Long getTicketId() {
                return 7L;
            }

            @Override
            public String getAttendeeName() {
                return "Mariam";
            }

            @Override
            public String getTicketCode() {
                return "TIX-UPCOMING-1";
            }

            @Override
            public Long getBookingId() {
                return 55L;
            }

            @Override
            public String getEventName() {
                return "Jazz Night";
            }

            @Override
            public LocalDateTime getEventDate() {
                return LocalDateTime.of(2026, 4, 10, 20, 0);
            }
        };

        when(ticketRepository.findUnusedTicketsForUpcomingEvents()).thenReturn(List.of(projection));

        List<UnusedTicketDTO> result = ticketService.getUnusedUpcomingTickets();

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getTicketId());
        assertEquals("Mariam", result.get(0).getAttendeeName());
        assertEquals("TIX-UPCOMING-1", result.get(0).getTicketCode());
        assertEquals(55L, result.get(0).getBookingId());
        assertEquals("Jazz Night", result.get(0).getEventName());
        assertEquals(LocalDateTime.of(2026, 4, 10, 20, 0), result.get(0).getEventDate());
    }

    @Test
    void getUnusedUpcomingTicketsReturnsEmptyListWhenRepositoryHasNoMatches() {
        when(ticketRepository.findUnusedTicketsForUpcomingEvents()).thenReturn(List.of());

        List<UnusedTicketDTO> result = ticketService.getUnusedUpcomingTickets();

        assertEquals(List.of(), result);
    }

    @Test
    void findTicketsNearVenueRejectsInvalidLatitude() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.findTicketsNearVenue(91.0, 31.0, 10.0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).findTicketsNearVenue(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void findTicketsNearVenueRejectsInvalidLongitude() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.findTicketsNearVenue(30.0, 181.0, 10.0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).findTicketsNearVenue(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void findTicketsNearVenueRejectsNonPositiveRadius() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.findTicketsNearVenue(30.0, 31.0, 0.0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).findTicketsNearVenue(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void findTicketsNearVenueMapsRepositoryResults() {
        NearbyTicketProjection projection = new NearbyTicketProjection() {
            @Override
            public Long getTicketId() {
                return 7L;
            }

            @Override
            public String getAttendeeName() {
                return "Mariam";
            }

            @Override
            public Long getBookingId() {
                return 55L;
            }

            @Override
            public String getEventName() {
                return "Jazz Night";
            }

            @Override
            public Double getEventLat() {
                return 30.0444;
            }

            @Override
            public Double getEventLon() {
                return 31.2357;
            }

            @Override
            public Double getDistanceKm() {
                return 1.25;
            }
        };

        when(ticketRepository.findTicketsNearVenue(30.0444, 31.2357, 5.0)).thenReturn(List.of(projection));

        List<NearbyTicketResponseDTO> result = ticketService.findTicketsNearVenue(30.0444, 31.2357, 5.0);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getTicketId());
        assertEquals("Mariam", result.get(0).getAttendeeName());
        assertEquals(55L, result.get(0).getBookingId());
        assertEquals("Jazz Night", result.get(0).getEventName());
        assertEquals(30.0444, result.get(0).getEventLat());
        assertEquals(31.2357, result.get(0).getEventLon());
        assertEquals(1.25, result.get(0).getDistanceKm());
    }

    private Ticket ticket(String attendeeName, String ticketCode) {
        Ticket ticket = new Ticket();
        ticket.setAttendeeName(attendeeName);
        ticket.setTicketCode(ticketCode);
        ticket.setMetadata(Map.of());
        return ticket;
    }

}
