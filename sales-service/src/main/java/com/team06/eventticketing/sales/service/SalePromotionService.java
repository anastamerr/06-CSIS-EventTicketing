package com.team06.eventticketing.sales.service;

import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.SalePromotionRequest;
import com.team06.eventticketing.sales.dto.SalePromotionResponse;
import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.repository.PromotionRepository;
import com.team06.eventticketing.sales.repository.SalePromotionRepository;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalePromotionService {

    private final SalePromotionRepository salePromotionRepository;
    private final TicketSaleRepository ticketSaleRepository;
    private final PromotionRepository promotionRepository;
    private final TicketSaleService ticketSaleService;

    public SalePromotionService(
            SalePromotionRepository salePromotionRepository,
            TicketSaleRepository ticketSaleRepository,
            PromotionRepository promotionRepository,
            TicketSaleService ticketSaleService
    ) {
        this.salePromotionRepository = salePromotionRepository;
        this.ticketSaleRepository = ticketSaleRepository;
        this.promotionRepository = promotionRepository;
        this.ticketSaleService = ticketSaleService;
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

    @Transactional
    public SaleDetailsDTO applyPromotionToTicketSale(Long saleId, Long promotionId) {
        TicketSale ticketSale = ticketSaleRepository.findByIdWithSalePromotionsForUpdate(saleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));
        validatePendingTicketSale(ticketSale);

        Promotion promotion = promotionRepository.findByIdForUpdate(promotionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found"));
        validatePromotionUsable(promotion);

        if (salePromotionRepository.existsByTicketSaleIdAndPromotionId(saleId, promotionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "promotion already applied");
        }

        SalePromotion salePromotion = new SalePromotion();
        ticketSale.addSalePromotion(salePromotion);
        promotion.addSalePromotion(salePromotion);
        salePromotion.setDiscountApplied(calculateDiscount(ticketSale, promotion));
        try {
            salePromotionRepository.saveAndFlush(salePromotion);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "promotion already applied", exception);
        }

        promotion.setCurrentUses(currentUses(promotion) + 1);
        promotionRepository.save(promotion);

        return ticketSaleService.getTicketSaleDetails(saleId);
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

    private void validatePendingTicketSale(TicketSale ticketSale) {
        if (ticketSale.getStatus() != TicketSaleStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cannot apply promotion to a completed/cancelled sale"
            );
        }
    }

    private void validatePromotionUsable(Promotion promotion) {
        if (!Boolean.TRUE.equals(promotion.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion is inactive");
        }

        if (promotion.getExpiryDate() == null || !promotion.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion is expired");
        }

        if (promotion.getMaxUses() != null && currentUses(promotion) >= promotion.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion usage limit reached");
        }
    }

    private double calculateDiscount(TicketSale ticketSale, Promotion promotion) {
        double saleAmount = ticketSale.getAmount() == null ? 0.0 : ticketSale.getAmount();
        double discountValue = promotion.getDiscountValue() == null ? 0.0 : promotion.getDiscountValue();

        double calculatedDiscount = switch (promotion.getDiscountType()) {
            case PERCENTAGE -> saleAmount * discountValue / 100.0;
            case FIXED -> discountValue;
        };

        return Math.min(calculatedDiscount, saleAmount);
    }

    private int currentUses(Promotion promotion) {
        return promotion.getCurrentUses() == null ? 0 : promotion.getCurrentUses();
    }
}
