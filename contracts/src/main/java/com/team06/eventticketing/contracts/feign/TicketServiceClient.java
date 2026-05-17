package com.team06.eventticketing.contracts.feign;

import com.team06.eventticketing.contracts.dto.EventTicketSummaryDTO;
import com.team06.eventticketing.contracts.feign.fallback.TicketServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-service", url = "${feign.ticket-service.url}", fallback = TicketServiceClientFallback.class)
public interface TicketServiceClient {

    @GetMapping("/api/tickets/event/{eventId}/summary")
    EventTicketSummaryDTO getEventTicketSummary(@PathVariable("eventId") Long eventId);

    @GetMapping("/api/tickets/booking/{bookingId}/used-count")
    int getActiveTicketCountForBooking(@PathVariable("bookingId") Long bookingId);

    default int getUsedTicketCountForBooking(Long bookingId) {
        return getActiveTicketCountForBooking(bookingId);
    }
}
