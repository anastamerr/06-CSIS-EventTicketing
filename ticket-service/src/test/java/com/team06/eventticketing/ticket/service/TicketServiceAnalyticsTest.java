package com.team06.eventticketing.ticket.service;

import com.team06.eventticketing.ticket.dto.TicketAnalyticsDTO;
import com.team06.eventticketing.ticket.model.TicketAnalyticsEvent;
import com.team06.eventticketing.ticket.model.TicketStatus;
import com.team06.eventticketing.ticket.repository.TicketAnalyticsEventRepository;
import com.team06.eventticketing.ticket.repository.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceAnalyticsTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAnalyticsEventRepository analyticsEventRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AutoCloseable mocks;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-15T10:00:00Z"), ZoneOffset.UTC);
        ticketService = new TicketService(ticketRepository, analyticsEventRepository, redisTemplate, fixedClock);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void shouldReturnTicketAnalyticsAndCacheResults() {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);
        String cacheKey = "ticket:analytics:2026-04-01:2026-04-30";

        when(valueOperations.get(eq(cacheKey))).thenReturn(null);
        when(ticketRepository.countByIssuedAtBetween(any(), any())).thenReturn(10L);
        when(ticketRepository.countStatusByIssuedAtBetween(any(), any())).thenReturn(
                List.of(
                        new Object[]{TicketStatus.USED, 6L},
                        new Object[]{TicketStatus.VALID, 2L},
                        new Object[]{TicketStatus.EXPIRED, 1L},
                        new Object[]{TicketStatus.CANCELLED, 1L}
                )
        );

        TicketAnalyticsDTO analytics = ticketService.getTicketAnalytics(startDate, endDate);

        assertEquals(10L, analytics.getTotalIssued());
        assertEquals(6L, analytics.getUsedCount());
        assertEquals(2L, analytics.getValidCount());
        assertEquals(1L, analytics.getExpiredCount());
        assertEquals(1L, analytics.getCancelledCount());
        assertEquals(0.6, analytics.getAttendanceRate());
        assertEquals(4, analytics.getTicketsByStatus().size());
        assertEquals(6L, analytics.getTicketsByStatus().get("USED"));
        verify(analyticsEventRepository).save(any(TicketAnalyticsEvent.class));
        verify(valueOperations).set(eq(cacheKey), any(String.class), any(java.time.Duration.class));
    }
}
