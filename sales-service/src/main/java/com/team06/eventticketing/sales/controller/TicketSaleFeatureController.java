package com.team06.eventticketing.sales.controller;

import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.sales.dto.ProcessBookingSaleRequest;
import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.dto.RevenueReportDTO;
import com.team06.eventticketing.sales.dto.SaleAuditTrailDTO;
import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.dto.TierRevenueDTO;
import com.team06.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.service.TicketSaleService;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class TicketSaleFeatureController {

    private final TicketSaleService ticketSaleService;

    public TicketSaleFeatureController(TicketSaleService ticketSaleService) {
        this.ticketSaleService = ticketSaleService;
    }

    @GetMapping("/{saleId}/details")
    @CachedFeature(service = "sales-service", featureId = "S5-F3", ttlSeconds = 600)
    public SaleDetailsDTO getTicketSaleDetails(@PathVariable Long saleId) {
        return ticketSaleService.getTicketSaleDetails(saleId);
    }

    @GetMapping("/{saleId}/audit-trail")
    @CachedFeature(service = "sales-service", featureId = "S5-F11", ttlSeconds = 600)
    public SaleAuditTrailDTO getSaleAuditTrail(@PathVariable Long saleId) {
        return ticketSaleService.getSaleAuditTrail(saleId);
    }

    @GetMapping("/analytics/tier")
    public List<TierRevenueDTO> getTierRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ticketSaleService.getTierRevenue(startDate, endDate);
    }

    @GetMapping("/user/{userId}/summary")
    @CachedFeature(service = "sales-service", featureId = "S5-F8", ttlSeconds = 600)
    public UserSaleSummaryDTO getUserSaleSummary(@PathVariable Long userId) {
        return ticketSaleService.getUserSaleSummary(userId);
    }

    @PostMapping("/booking/{bookingId}")
    @ResponseStatus(HttpStatus.CREATED)
    @InvalidateServiceCaches(service = "sales-service", featurePrefix = "S5-")
    public TicketSaleResponse processBookingSale(
            @PathVariable Long bookingId,
            @RequestBody ProcessBookingSaleRequest request,
            @RequestParam(defaultValue = "false") boolean simulateFailure
    ) {
        return ticketSaleService.processBookingSale(bookingId, request, simulateFailure);
    }

    @PutMapping("/{id}/retry")
    @InvalidateServiceCaches(
            service = "sales-service",
            featurePrefix = "S5-",
            detailKeys = {
                    "'sales-service::ticket-sale::' + #id",
                    "'sales-service::sale-audit-trail::' + #id"
            })
    public TicketSale retrySale(@PathVariable Long id) {
        return ticketSaleService.retryFailedSale(id);
    }

    @PutMapping("/{id}/refund")
    @InvalidateServiceCaches(
            service = "sales-service",
            featurePrefix = "S5-",
            detailKeys = {
                    "'sales-service::ticket-sale::' + #id",
                    "'sales-service::sale-audit-trail::' + #id"
            })
    public TicketSale refundSale(@PathVariable Long id, @RequestBody(required = false) RefundRequest request) {
        return ticketSaleService.refundTicketSale(id, request);
    }

    @GetMapping("/reports/revenue")
    @CachedFeature(service = "sales-service", featureId = "S5-F6", ttlSeconds = 600)
    public RevenueReportDTO getRevenueReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ticketSaleService.getRevenueReport(startDate, endDate);
    }

    @GetMapping("/search")
    @CachedFeature(service = "sales-service", featureId = "S5-F1", ttlSeconds = 300)
    public List<TicketSaleResponse> searchTicketSales(
            @RequestParam(required = false) TicketSaleStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ticketSaleService.searchTicketSales(status, startDate, endDate);
    }

    @PostMapping("/{id}/refund-window-policy")
    @InvalidateServiceCaches(
            service = "sales-service",
            featurePrefix = "S5-",
            detailKeys = {
                    "'sales-service::ticket-sale::' + #id",
                    "'sales-service::sale-audit-trail::' + #id"
            })
    public TicketSaleResponse refundSaleWithWindowPolicy(
            @PathVariable Long id,
            @RequestBody(required = false) RefundRequest request
    ) {
        return ticketSaleService.processRefundWithWindowPolicy(id, request);
    }


}
