package com.team06.eventticketing.contracts.feign.fallback;

import com.team06.eventticketing.contracts.dto.ProcessBookingSaleRequest;
import com.team06.eventticketing.contracts.dto.TicketSaleResponseDTO;
import com.team06.eventticketing.contracts.dto.UserSaleSummaryDTO;
import com.team06.eventticketing.contracts.feign.SalesServiceClient;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SalesServiceClientFallback implements SalesServiceClient {

    @Override
    public UserSaleSummaryDTO getUserSaleSummary(Long userId) {
        return new UserSaleSummaryDTO(userId, 0L, 0.0, Map.of(), Map.of());
    }

    @Override
    public TicketSaleResponseDTO processBookingSale(
            Long bookingId,
            ProcessBookingSaleRequest request,
            boolean simulateFailure
    ) {
        return new TicketSaleResponseDTO(null, bookingId, null, 0.0, null, "PAYMENT_FAILED", Map.of(), null);
    }
}
