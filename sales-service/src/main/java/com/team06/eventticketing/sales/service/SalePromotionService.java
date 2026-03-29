package com.team06.eventticketing.sales.service;

import com.team06.eventticketing.sales.dto.SalePromotionRequest;
import com.team06.eventticketing.sales.dto.SalePromotionResponse;
import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.repository.PromotionRepository;
import com.team06.eventticketing.sales.repository.SalePromotionRepository;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalePromotionService {

    private final SalePromotionRepository salePromotionRepository;
    private final TicketSaleRepository ticketSaleRepository;
    private final PromotionRepository promotionRepository;

    public SalePromotionService(
            SalePromotionRepository salePromotionRepository,
            TicketSaleRepository ticketSaleRepository,
            PromotionRepository promotionRepository
    ) {
        this.salePromotionRepository = salePromotionRepository;
        this.ticketSaleRepository = ticketSaleRepository;
        this.promotionRepository = promotionRepository;
    }

    @Transactional(readOnly = true)
    public List<SalePromotionResponse> getAllSalePromotions() {
        return salePromotionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SalePromotionResponse getSalePromotionById(Long id) {
        return toResponse(findSalePromotion(id));
    }

    @Transactional
    public SalePromotionResponse createSalePromotion(SalePromotionRequest request) {
        SalePromotion salePromotion = new SalePromotion();
        apply(salePromotion, request);
        return toResponse(salePromotionRepository.save(salePromotion));
    }

    @Transactional
    public SalePromotionResponse updateSalePromotion(Long id, SalePromotionRequest request) {
        SalePromotion existing = findSalePromotion(id);
        apply(existing, request);
        return toResponse(salePromotionRepository.save(existing));
    }

    @Transactional
    public void deleteSalePromotion(Long id) {
        salePromotionRepository.delete(findSalePromotion(id));
    }

    private SalePromotion findSalePromotion(Long id) {
        return salePromotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale promotion not found"));
    }

    private void apply(SalePromotion salePromotion, SalePromotionRequest request) {
        TicketSale ticketSale = ticketSaleRepository.findById(request.getTicketSaleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));
        Promotion promotion = promotionRepository.findById(request.getPromotionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found"));
        salePromotion.setTicketSale(ticketSale);
        salePromotion.setPromotion(promotion);
        salePromotion.setDiscountApplied(request.getDiscountApplied());
        salePromotion.setAppliedAt(request.getAppliedAt());
    }

    private SalePromotionResponse toResponse(SalePromotion salePromotion) {
        SalePromotionResponse response = new SalePromotionResponse();
        response.setId(salePromotion.getId());
        response.setTicketSaleId(salePromotion.getTicketSale() == null ? null : salePromotion.getTicketSale().getId());
        response.setPromotionId(salePromotion.getPromotion() == null ? null : salePromotion.getPromotion().getId());
        response.setDiscountApplied(salePromotion.getDiscountApplied());
        response.setAppliedAt(salePromotion.getAppliedAt());
        return response;
    }
}
