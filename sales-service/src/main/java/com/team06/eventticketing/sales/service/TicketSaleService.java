package com.team06.eventticketing.sales.service;

import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketSaleService {

    private final TicketSaleRepository ticketSaleRepository;

    public TicketSaleService(TicketSaleRepository ticketSaleRepository) {
        this.ticketSaleRepository = ticketSaleRepository;
    }

    @Transactional
    public TicketSale retryFailedSale(Long id) {
        TicketSale sale = ticketSaleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));

        if (sale.getStatus() != TicketSaleStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale is not in FAILED status");
        }

        Map<String, Object> details = sale.getTransactionDetails();
        if (details == null) {
            details = new LinkedHashMap<>();
        }

        int retryAttempt = 0;
        Object rawRetry = details.get("retryAttempt");
        if (rawRetry instanceof Number) {
            retryAttempt = ((Number) rawRetry).intValue();
        }

        details.put("retryAttempt", retryAttempt + 1);
        details.put("gatewayResponse", "approved");
        sale.setTransactionDetails(details);
        sale.setStatus(TicketSaleStatus.COMPLETED);

        return ticketSaleRepository.save(sale);
    }
}
