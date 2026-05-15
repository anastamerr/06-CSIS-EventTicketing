package com.team06.eventticketing.ticket.client;

import com.team06.eventticketing.contracts.dto.BookingDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "booking-service", url = "${feign.booking-service.url}")
public interface BookingServiceClient {

    @GetMapping("/api/bookings/{id}")
    BookingDTO getBooking(@PathVariable("id") Long bookingId);
}
