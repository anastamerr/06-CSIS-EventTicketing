package com.team06.eventticketing.sales.controller;

import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.service.TicketSaleService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class TicketSaleController {

    private final TicketSaleService ticketSaleService;

    public TicketSaleController(TicketSaleService ticketSaleService) {
        this.ticketSaleService = ticketSaleService;
    }

    @PutMapping("/{id}/retry")
    public TicketSale retrySale(@PathVariable Long id) {
        return ticketSaleService.retryFailedSale(id);
    }
}
