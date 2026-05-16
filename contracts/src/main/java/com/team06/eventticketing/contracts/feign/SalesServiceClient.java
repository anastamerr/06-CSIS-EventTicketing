package com.team06.eventticketing.contracts.feign;

import com.team06.eventticketing.contracts.dto.ProcessBookingSaleRequest;
import com.team06.eventticketing.contracts.dto.TicketSaleResponseDTO;
import com.team06.eventticketing.contracts.dto.UserSaleSummaryDTO;
import com.team06.eventticketing.contracts.feign.fallback.SalesServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "sales-service", url = "${feign.sales-service.url}", fallback = SalesServiceClientFallback.class)
public interface SalesServiceClient {

    @GetMapping("/api/sales/user/{userId}/summary")
    UserSaleSummaryDTO getUserSaleSummary(@PathVariable Long userId);

    @PostMapping("/api/sales/booking/{bookingId}")
    TicketSaleResponseDTO processBookingSale(
            @PathVariable Long bookingId,
            @RequestBody ProcessBookingSaleRequest request,
            @RequestParam(defaultValue = "false") boolean simulateFailure
    );
}
