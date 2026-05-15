package com.team06.eventticketing.contracts.feign;

import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.contracts.dto.BookingItemDTO;
import com.team06.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team06.eventticketing.contracts.dto.EventBookingRevenueDTO;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {

    @GetMapping("/api/bookings/event/{eventId}/revenue")
    EventBookingRevenueDTO getEventRevenue(
            @PathVariable Long eventId,
            @RequestParam String startDate,
            @RequestParam String endDate
    );

    @GetMapping("/api/bookings/event/{eventId}/active-count")
    int getEventActiveBookingCount(@PathVariable Long eventId);

    @GetMapping("/api/bookings/{bookingId}")
    BookingDTO getBooking(@PathVariable Long bookingId);

    @GetMapping("/api/bookings/{bookingId}/items")
    List<BookingItemDTO> getBookingItems(@PathVariable Long bookingId);

    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable Long userId);

    @GetMapping("/api/bookings/user/{userId}/active-count")
    int getUserActiveBookingCount(@PathVariable Long userId);

    default int getActiveBookingCount(Long userId) {
        return getUserActiveBookingCount(userId);
    }

    @GetMapping("/api/bookings/user/{userId}/count")
    long getUserBookingCount(@PathVariable Long userId, @RequestParam(required = false) String status);

    default long getTotalBookingCount(Long userId, String status) {
        return getUserBookingCount(userId, status);
    }

    @GetMapping("/api/bookings/user/{userId}/total")
    BigDecimal getUserCompletedBookingTotal(
            @PathVariable Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate
    );

    default BigDecimal getUserBookingTotal(Long userId, String startDate, String endDate) {
        return getUserCompletedBookingTotal(userId, startDate, endDate);
    }
}
