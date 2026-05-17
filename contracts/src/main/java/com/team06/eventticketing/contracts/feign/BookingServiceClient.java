package com.team06.eventticketing.contracts.feign;

import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.contracts.dto.BookingItemDTO;
import com.team06.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team06.eventticketing.contracts.dto.EventBookingRevenueDTO;
import com.team06.eventticketing.contracts.feign.fallback.BookingServiceClientFallback;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url}", fallback = BookingServiceClientFallback.class)
public interface BookingServiceClient {

    @GetMapping("/api/bookings/event/{eventId}/revenue")
    EventBookingRevenueDTO getEventRevenue(
            @PathVariable("eventId") Long eventId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    @GetMapping("/api/bookings/event/{eventId}/active-count")
    int getEventActiveBookingCount(@PathVariable("eventId") Long eventId);

    @GetMapping("/api/bookings/{bookingId}")
    BookingDTO getBooking(@PathVariable("bookingId") Long bookingId);

    @GetMapping("/api/bookings/{bookingId}/items")
    List<BookingItemDTO> getBookingItems(@PathVariable("bookingId") Long bookingId);

    @GetMapping("/api/bookings/user/{userId}/summary")
    BookingSummaryDTO getUserBookingSummary(@PathVariable("userId") Long userId);

    @GetMapping("/api/bookings/user/{userId}/active-count")
    int getUserActiveBookingCount(@PathVariable("userId") Long userId);

    default int getActiveBookingCount(Long userId) {
        return getUserActiveBookingCount(userId);
    }

    @GetMapping("/api/bookings/user/{userId}/count")
    long getUserBookingCount(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "status", required = false) String status
    );

    default long getTotalBookingCount(Long userId, String status) {
        return getUserBookingCount(userId, status);
    }

    @GetMapping("/api/bookings/user/{userId}/total")
    BigDecimal getUserCompletedBookingTotal(
            @PathVariable("userId") Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    default BigDecimal getUserBookingTotal(Long userId, String startDate, String endDate) {
        return getUserCompletedBookingTotal(userId, startDate, endDate);
    }
}
