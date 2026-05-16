package com.team06.eventticketing.contracts.feign.fallback;

import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.contracts.dto.BookingItemDTO;
import com.team06.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team06.eventticketing.contracts.dto.EventBookingRevenueDTO;
import com.team06.eventticketing.contracts.feign.BookingServiceClient;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BookingServiceClientFallback implements BookingServiceClient {

    @Override
    public EventBookingRevenueDTO getEventRevenue(Long eventId, String startDate, String endDate) {
        return new EventBookingRevenueDTO(0, 0.0, 0.0);
    }

    @Override
    public int getEventActiveBookingCount(Long eventId) {
        return 0;
    }

    @Override
    public BookingDTO getBooking(Long bookingId) {
        return null;
    }

    @Override
    public List<BookingItemDTO> getBookingItems(Long bookingId) {
        return List.of();
    }

    @Override
    public BookingSummaryDTO getUserBookingSummary(Long userId) {
        return new BookingSummaryDTO(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    public int getUserActiveBookingCount(Long userId) {
        return 0;
    }

    @Override
    public long getUserBookingCount(Long userId, String status) {
        return 0;
    }

    @Override
    public BigDecimal getUserCompletedBookingTotal(Long userId, String startDate, String endDate) {
        return BigDecimal.ZERO;
    }
}
