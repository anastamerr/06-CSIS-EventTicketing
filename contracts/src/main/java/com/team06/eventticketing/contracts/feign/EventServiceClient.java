package com.team06.eventticketing.contracts.feign;

import com.team06.eventticketing.contracts.dto.AvgCapacityDTO;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.dto.VenueCoordsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "event-service", url = "${feign.event-service.url}")
public interface EventServiceClient {

    @GetMapping("/api/events/{id}")
    EventDTO getEvent(@PathVariable Long id);

    @GetMapping("/api/events/{id}/sessions/avg-capacity")
    AvgCapacityDTO getEventAvgCapacity(@PathVariable Long id);

    default AvgCapacityDTO getEventAverageSessionCapacity(Long id) {
        return getEventAvgCapacity(id);
    }

    @GetMapping("/api/events/{id}/venue-coords")
    VenueCoordsDTO getEventVenueCoords(@PathVariable Long id);
}
