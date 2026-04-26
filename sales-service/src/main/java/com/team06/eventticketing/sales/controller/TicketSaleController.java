package com.team06.eventticketing.sales.controller;

import com.team06.eventticketing.common.cache.CachedDetail;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.sales.dto.TicketSaleRequest;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.service.TicketSaleService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class TicketSaleController {

    private final TicketSaleService ticketSaleService;

    public TicketSaleController(TicketSaleService ticketSaleService) {
        this.ticketSaleService = ticketSaleService;
    }

    @GetMapping
    public List<TicketSaleResponse> getAllTicketSales() {
        return ticketSaleService.getAllTicketSales();
    }

    @GetMapping("/{id}")
    @CachedDetail(service = "sales-service", entity = "ticket-sale", key = "#id", ttlSeconds = 900)
    public TicketSaleResponse getTicketSaleById(@PathVariable Long id) {
        return ticketSaleService.getTicketSaleById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @InvalidateServiceCaches(service = "sales-service", featurePrefix = "S5-")
    public TicketSaleResponse createTicketSale(@RequestBody TicketSaleRequest request) {
        return ticketSaleService.createTicketSale(request);
    }

    @PutMapping("/{id}")
    @InvalidateServiceCaches(
            service = "sales-service",
            featurePrefix = "S5-",
            detailKeys = {
                    "'sales-service::ticket-sale::' + #id",
                    "'sales-service::sale-audit-trail::' + #id"
            })
    public TicketSaleResponse updateTicketSale(@PathVariable Long id, @RequestBody TicketSaleRequest request) {
        return ticketSaleService.updateTicketSale(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @InvalidateServiceCaches(
            service = "sales-service",
            featurePrefix = "S5-",
            detailKeys = {
                    "'sales-service::ticket-sale::' + #id",
                    "'sales-service::sale-audit-trail::' + #id"
            })
    public void deleteTicketSale(@PathVariable Long id) {
        ticketSaleService.deleteTicketSale(id);
    }
}
