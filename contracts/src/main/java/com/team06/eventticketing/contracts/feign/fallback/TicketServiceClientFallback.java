package com.team06.eventticketing.contracts.feign.fallback;

import com.team06.eventticketing.contracts.dto.EventTicketSummaryDTO;
import com.team06.eventticketing.contracts.feign.TicketServiceClient;
import org.springframework.stereotype.Component;

@Component
public class TicketServiceClientFallback implements TicketServiceClient {

    @Override
    public EventTicketSummaryDTO getEventTicketSummary(Long eventId) {
        return new EventTicketSummaryDTO(0, 0);
    }

    @Override
    public int getActiveTicketCountForBooking(Long bookingId) {
        return 0;
    }
}
