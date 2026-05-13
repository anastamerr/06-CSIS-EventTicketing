package com.team06.eventticketing.ticket.client;

import com.team06.eventticketing.ticket.dto.VenueCoordsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "event-service", url = "${feign.event-service.url}")
public interface EventServiceClient {

    @GetMapping("/api/events/{eventId}/venue-coords")
    VenueCoordsDTO getVenueCoords(@PathVariable("eventId") Long eventId);

    @GetMapping("/api/events/{eventId}")
    EventResponse getEvent(@PathVariable("eventId") Long eventId);

    record EventResponse(Long id, String name, String status, java.time.LocalDateTime eventDate) {}
}