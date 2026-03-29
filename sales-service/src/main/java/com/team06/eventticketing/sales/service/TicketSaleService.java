package com.team06.eventticketing.sales.service;

import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.TicketSaleRequest;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import java.util.List;
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

    @Transactional(readOnly = true)
    public List<TicketSaleResponse> getAllTicketSales() {
        return ticketSaleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TicketSaleResponse getTicketSaleById(Long id) {
        return toResponse(findTicketSale(id));
    }

    @Transactional(readOnly = true)
    public SaleDetailsDTO getTicketSaleDetails(Long id) {
        TicketSale ticketSale = ticketSaleRepository.findByIdWithSalePromotions(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));

        List<SaleDetailsDTO.AppliedPromotionDTO> appliedPromotions = ticketSale.getSalePromotions().stream()
                .sorted((first, second) -> first.getAppliedAt().compareTo(second.getAppliedAt()))
                .map(this::toAppliedPromotion)
                .toList();

        double totalDiscount = appliedPromotions.stream()
                .mapToDouble(promotion -> promotion.getDiscountApplied() == null ? 0.0 : promotion.getDiscountApplied())
                .sum();
        double originalAmount = ticketSale.getAmount() == null ? 0.0 : ticketSale.getAmount();

        SaleDetailsDTO response = new SaleDetailsDTO();
        response.setSaleId(ticketSale.getId());
        response.setBookingId(ticketSale.getBookingId());
        response.setUserId(ticketSale.getUserId());
        response.setOriginalAmount(originalAmount);
        response.setMethod(ticketSale.getMethod());
        response.setStatus(ticketSale.getStatus());
        response.setTransactionDetails(copyTransactionDetails(ticketSale.getTransactionDetails()));
        response.setAppliedPromotions(appliedPromotions);
        response.setTotalDiscount(totalDiscount);
        response.setFinalAmount(originalAmount - totalDiscount);
        return response;
    }

    @Transactional
    public TicketSaleResponse createTicketSale(TicketSaleRequest request) {
        TicketSale ticketSale = new TicketSale();
        apply(ticketSale, request);
        return toResponse(ticketSaleRepository.save(ticketSale));
    }

    @Transactional
    public TicketSaleResponse updateTicketSale(Long id, TicketSaleRequest request) {
        TicketSale existing = findTicketSale(id);
        apply(existing, request);
        return toResponse(ticketSaleRepository.save(existing));
    }

    @Transactional
    public void deleteTicketSale(Long id) {
        ticketSaleRepository.delete(findTicketSale(id));
    }

    private TicketSale findTicketSale(Long id) {
        return ticketSaleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));
    }

    private void apply(TicketSale ticketSale, TicketSaleRequest request) {
        ticketSale.setBookingId(request.getBookingId());
        ticketSale.setUserId(request.getUserId());
        ticketSale.setAmount(request.getAmount());
        ticketSale.setMethod(request.getMethod());
        ticketSale.setStatus(request.getStatus());
        ticketSale.setTransactionDetails(request.getTransactionDetails());
    }

    private TicketSaleResponse toResponse(TicketSale ticketSale) {
        TicketSaleResponse response = new TicketSaleResponse();
        response.setId(ticketSale.getId());
        response.setBookingId(ticketSale.getBookingId());
        response.setUserId(ticketSale.getUserId());
        response.setAmount(ticketSale.getAmount());
        response.setMethod(ticketSale.getMethod());
        response.setStatus(ticketSale.getStatus());
        response.setTransactionDetails(ticketSale.getTransactionDetails());
        response.setCreatedAt(ticketSale.getCreatedAt());
        return response;
    }

    private SaleDetailsDTO.AppliedPromotionDTO toAppliedPromotion(SalePromotion salePromotion) {
        SaleDetailsDTO.AppliedPromotionDTO response = new SaleDetailsDTO.AppliedPromotionDTO();
        response.setPromotionCode(salePromotion.getPromotion().getCode());
        response.setDiscountType(salePromotion.getPromotion().getDiscountType());
        response.setDiscountApplied(salePromotion.getDiscountApplied());
        response.setAppliedAt(salePromotion.getAppliedAt());
        return response;
    }

    private Map<String, Object> copyTransactionDetails(Map<String, Object> transactionDetails) {
        return transactionDetails == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(transactionDetails);
    }
}
