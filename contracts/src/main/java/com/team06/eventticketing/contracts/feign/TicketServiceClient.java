package com.team06.eventticketing.contracts.feign;

import com.team06.eventticketing.contracts.dto.EventTicketSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ticket-service", url = "${feign.ticket-service.url}")
public interface TicketServiceClient {

    @GetMapping("/api/tickets/event/{eventId}/summary")
    EventTicketSummaryDTO getEventTicketSummary(@PathVariable Long eventId);

    @GetMapping("/api/tickets/booking/{bookingId}/used-count")
    int getUsedTicketCountForBooking(@PathVariable Long bookingId);
}
