package com.team06.eventticketing.ticket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.ticket.dto.NearbyTicketResponseDTO;
import com.team06.eventticketing.ticket.dto.PurgeTicketsResponseDTO;
import com.team06.eventticketing.ticket.model.Ticket;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.NearbyTicketProjection;
import com.team06.eventticketing.ticket.repository.TicketRepository;
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
    void findTicketsNearVenueRejectsInvalidLatitude() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.findTicketsNearVenue(91.0, 31.0, 10.0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).findTicketsNearVenue(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void findTicketsNearVenueRejectsInvalidLongitude() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.findTicketsNearVenue(30.0, 181.0, 10.0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).findTicketsNearVenue(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void findTicketsNearVenueRejectsNonPositiveRadius() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketService.findTicketsNearVenue(30.0, 31.0, 0.0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketRepository, never()).findTicketsNearVenue(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void findTicketsNearVenueMapsRepositoryResults() {
        NearbyTicketProjection projection = new TestNearbyTicketProjection(
                7L, 55L, 88L, "Jazz Night", "Opera House", "Mariam", "TIX-7",
                "VALID", LocalDateTime.parse("2026-03-29T12:00:00"), 30.0444, 31.2357, 1.25
        );

        when(ticketRepository.findTicketsNearVenue(30.0444, 31.2357, 5.0)).thenReturn(List.of(projection));

        List<NearbyTicketResponseDTO> result = ticketService.findTicketsNearVenue(30.0444, 31.2357, 5.0);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getTicketId());
        assertEquals(88L, result.get(0).getEventId());
        assertEquals("Jazz Night", result.get(0).getEventName());
        assertEquals(1.25, result.get(0).getDistanceKm());
    }

    private Ticket ticket(String attendeeName, String ticketCode) {
        Ticket ticket = new Ticket();
        ticket.setAttendeeName(attendeeName);
        ticket.setTicketCode(ticketCode);
        ticket.setMetadata(Map.of());
        return ticket;
    }

    private static class TestNearbyTicketProjection implements NearbyTicketProjection {

        private final Long ticketId;
        private final Long bookingId;
        private final Long eventId;
        private final String eventName;
        private final String venue;
        private final String attendeeName;
        private final String ticketCode;
        private final String ticketStatus;
        private final LocalDateTime issuedAt;
        private final Double venueLatitude;
        private final Double venueLongitude;
        private final Double distanceKm;

        private TestNearbyTicketProjection(
                Long ticketId,
                Long bookingId,
                Long eventId,
                String eventName,
                String venue,
                String attendeeName,
                String ticketCode,
                String ticketStatus,
                LocalDateTime issuedAt,
                Double venueLatitude,
                Double venueLongitude,
                Double distanceKm
        ) {
            this.ticketId = ticketId;
            this.bookingId = bookingId;
            this.eventId = eventId;
            this.eventName = eventName;
            this.venue = venue;
            this.attendeeName = attendeeName;
            this.ticketCode = ticketCode;
            this.ticketStatus = ticketStatus;
            this.issuedAt = issuedAt;
            this.venueLatitude = venueLatitude;
            this.venueLongitude = venueLongitude;
            this.distanceKm = distanceKm;
        }

        @Override
        public Long getTicketId() {
            return ticketId;
        }

        @Override
        public Long getBookingId() {
            return bookingId;
        }

        @Override
        public Long getEventId() {
            return eventId;
        }

        @Override
        public String getEventName() {
            return eventName;
        }

        @Override
        public String getVenue() {
            return venue;
        }

        @Override
        public String getAttendeeName() {
            return attendeeName;
        }

        @Override
        public String getTicketCode() {
            return ticketCode;
        }

        @Override
        public String getTicketStatus() {
            return ticketStatus;
        }

        @Override
        public LocalDateTime getIssuedAt() {
            return issuedAt;
        }

        @Override
        public Double getVenueLatitude() {
            return venueLatitude;
        }

        @Override
        public Double getVenueLongitude() {
            return venueLongitude;
        }

        @Override
        public Double getDistanceKm() {
            return distanceKm;
        }
    }
}
