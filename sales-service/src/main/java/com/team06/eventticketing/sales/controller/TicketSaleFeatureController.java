package com.team06.eventticketing.sales.controller;

import com.team06.eventticketing.sales.dto.ProcessBookingSaleRequest;
import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.service.TicketSaleService;
import org.springframework.http.HttpStatus;
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
public class TicketSaleFeatureController {

    private final TicketSaleService ticketSaleService;

    public TicketSaleFeatureController(TicketSaleService ticketSaleService) {
        this.ticketSaleService = ticketSaleService;
    }

    @GetMapping("/{saleId}/details")
    public SaleDetailsDTO getTicketSaleDetails(@PathVariable Long saleId) {
        return ticketSaleService.getTicketSaleDetails(saleId);
    }

    @PostMapping("/booking/{bookingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketSaleResponse processBookingSale(
            @PathVariable Long bookingId,
            @RequestBody ProcessBookingSaleRequest request
    ) {
        return ticketSaleService.processBookingSale(bookingId, request);
    }

    @PutMapping("/{id}/retry")
    public TicketSale retrySale(@PathVariable Long id) {
        return ticketSaleService.retryFailedSale(id);
    }

    @PutMapping("/{id}/refund")
    public TicketSale refundSale(@PathVariable Long id, @RequestBody RefundRequest request) {
        return ticketSaleService.refundTicketSale(id, request);
    }
}
