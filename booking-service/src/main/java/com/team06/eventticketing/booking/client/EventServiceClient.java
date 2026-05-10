package com.team06.eventticketing.booking.client;

import com.team06.eventticketing.booking.dto.EventDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.team06.eventticketing.booking.dto.AvgCapacityDTO;
@FeignClient(
        name = "event-service",
        url = "${feign.event-service.url}",
        configuration = FeignAuthConfig.class
)
public interface EventServiceClient {

    @GetMapping("/api/events/{id}")
    EventDTO getEvent(@PathVariable Long id);
    @GetMapping("/api/events/{eventId}/sessions/avg-capacity")
    AvgCapacityDTO getEventAvgCapacity(@PathVariable Long eventId);
}