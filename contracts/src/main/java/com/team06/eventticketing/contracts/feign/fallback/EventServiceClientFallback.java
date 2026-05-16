package com.team06.eventticketing.contracts.feign.fallback;

import com.team06.eventticketing.contracts.dto.AvgCapacityDTO;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.dto.VenueCoordsDTO;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
import org.springframework.stereotype.Component;

@Component
public class EventServiceClientFallback implements EventServiceClient {

    @Override
    public EventDTO getEvent(Long id) {
        return null;
    }

    @Override
    public AvgCapacityDTO getEventAvgCapacity(Long id) {
        return new AvgCapacityDTO(null);
    }

    @Override
    public VenueCoordsDTO getEventVenueCoords(Long id) {
        return new VenueCoordsDTO(null, null);
    }
}
