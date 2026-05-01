package com.team06.eventticketing.ticket.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.ticket.dto.TicketAnalyticsDTO;
import com.team06.eventticketing.ticket.service.TicketService;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketService ticketService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketController(ticketService)).build();
    }

    @Test
    void getTicketAnalyticsLogsViewAndReturnsSerializedDashboard() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);
        TicketAnalyticsDTO dashboard = TicketAnalyticsDTO.builder()
                .totalIssued(10L)
                .usedCount(6L)
                .validCount(2L)
                .expiredCount(1L)
                .cancelledCount(1L)
                .attendanceRate(0.6)
                .ticketsByStatus(Map.of(
                        "USED", 6L,
                        "VALID", 2L,
                        "EXPIRED", 1L,
                        "CANCELLED", 1L))
                .build();

        when(ticketService.getTicketAnalytics(startDate, endDate)).thenReturn(dashboard);

        mockMvc.perform(get("/api/tickets/analytics")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIssued", is(10)))
                .andExpect(jsonPath("$.usedCount", is(6)))
                .andExpect(jsonPath("$.validCount", is(2)))
                .andExpect(jsonPath("$.expiredCount", is(1)))
                .andExpect(jsonPath("$.cancelledCount", is(1)))
                .andExpect(jsonPath("$.attendanceRate", is(0.6)))
                .andExpect(jsonPath("$.ticketsByStatus.USED", is(6)));

        verify(ticketService).logAnalyticsViewed(startDate, endDate);
        verify(ticketService).getTicketAnalytics(startDate, endDate);
    }

    @Test
    void getTicketAnalyticsReturnsBadRequestForInvalidDateRange() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 5, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);

        doThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "startDate must not be after endDate"))
                .when(ticketService).logAnalyticsViewed(startDate, endDate);

        mockMvc.perform(get("/api/tickets/analytics")
                        .param("startDate", "2026-05-01")
                        .param("endDate", "2026-04-30"))
                .andExpect(status().isBadRequest());

        verify(ticketService).logAnalyticsViewed(startDate, endDate);
        verify(ticketService, never()).getTicketAnalytics(startDate, endDate);
    }
}
