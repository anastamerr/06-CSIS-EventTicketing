package com.team06.eventticketing.booking.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.booking.dto.BookingAnalyticsDashboardDTO;
import com.team06.eventticketing.booking.service.BookingService;
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
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingController(bookingService)).build();
    }

    @Test
    void getBookingAnalyticsDashboardReturnsSerializedDashboard() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        BookingAnalyticsDashboardDTO dashboard = BookingAnalyticsDashboardDTO.builder()
                .totalBookings(6L)
                .totalRevenue(1500.0)
                .averageBookingValue(750.0)
                .conversionRate(4.0 / 6.0)
                .bookingsByStatus(Map.of(
                        "PENDING", 1L,
                        "CONFIRMED", 1L,
                        "CHECKED_IN", 1L,
                        "COMPLETED", 2L,
                        "CANCELLED", 1L))
                .build();

        when(bookingService.getBookingAnalyticsDashboard(startDate, endDate)).thenReturn(dashboard);

        mockMvc.perform(get("/api/bookings/analytics/dashboard")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings", is(6)))
                .andExpect(jsonPath("$.totalRevenue", is(1500.0)))
                .andExpect(jsonPath("$.averageBookingValue", is(750.0)))
                .andExpect(jsonPath("$.conversionRate", is(4.0 / 6.0)))
                .andExpect(jsonPath("$.bookingsByStatus.COMPLETED", is(2)));

        verify(bookingService).getBookingAnalyticsDashboard(startDate, endDate);
    }

    @Test
    void getBookingAnalyticsDashboardReturnsBadRequestForInvalidDateRange() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);

        when(bookingService.getBookingAnalyticsDashboard(startDate, endDate))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "startDate must be on or before endDate"));

        mockMvc.perform(get("/api/bookings/analytics/dashboard")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isBadRequest());

        verify(bookingService).getBookingAnalyticsDashboard(startDate, endDate);
    }
}
