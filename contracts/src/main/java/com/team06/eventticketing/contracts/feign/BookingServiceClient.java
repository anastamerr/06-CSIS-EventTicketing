package com.team06.eventticketing.contracts.feign;

import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.contracts.dto.EventBookingRevenueDTO;
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
}
