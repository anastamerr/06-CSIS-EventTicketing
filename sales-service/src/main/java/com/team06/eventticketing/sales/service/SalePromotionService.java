package com.team06.eventticketing.sales.service;

import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalePromotionService {

    private final SalePromotionRepository salePromotionRepository;
    private final TicketSaleRepository ticketSaleRepository;
    private final PromotionRepository promotionRepository;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    public SalePromotionService(
            SalePromotionRepository salePromotionRepository,
            TicketSaleRepository ticketSaleRepository,
            PromotionRepository promotionRepository,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.salePromotionRepository = salePromotionRepository;
        this.ticketSaleRepository = ticketSaleRepository;
        this.promotionRepository = promotionRepository;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

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
        SalePromotion saved = salePromotionRepository.save(salePromotion);
        notifyObservers("SALE_PROMOTION_CREATED", buildAuditPayload(saved, null));
        return toResponse(saved);
    }

    @Transactional
    public SalePromotionResponse updateSalePromotion(Long id, SalePromotionRequest request) {
        SalePromotion existing = findSalePromotion(id);
        apply(existing, request);
        SalePromotion saved = salePromotionRepository.save(existing);
        notifyObservers("SALE_PROMOTION_UPDATED", buildAuditPayload(saved, null));
        return toResponse(saved);
    }

    @Transactional
    public void deleteSalePromotion(Long id) {
        SalePromotion salePromotion = findSalePromotion(id);
        salePromotionRepository.delete(salePromotion);
        notifyObservers("SALE_PROMOTION_DELETED", buildAuditPayload(salePromotion, null));
    }

    @Transactional
    public TicketSale applyPromotionToTicketSale(Long saleId, Long promotionId) {
        TicketSale ticketSale = findTicketSaleWithPromotionsForUpdate(saleId);
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
        salePromotion.setAppliedAt(LocalDateTime.now());
        try {
            salePromotion = salePromotionRepository.saveAndFlush(salePromotion);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "promotion already applied", exception);
        }

        promotion.setCurrentUses(currentUses(promotion) + 1);
        promotionRepository.save(promotion);
        notifyObservers(
                "PROMOTION_APPLIED",
                buildAuditPayload(
                        salePromotion,
                        Map.of("promotionCode", promotion.getCode(), "timesUsed", promotion.getCurrentUses())
                ));

        return findTicketSaleWithPromotions(saleId);
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

    private TicketSale findTicketSaleWithPromotionsForUpdate(Long saleId) {
        return ticketSaleRepository.findByIdWithSalePromotionsForUpdate(saleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));
    }

    private TicketSale findTicketSaleWithPromotions(Long saleId) {
        return ticketSaleRepository.findByIdWithSalePromotions(saleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));
    }

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String action, Object payload) {
        observers.forEach(observer -> observer.onEvent(action, payload));
    }

    private Map<String, Object> buildAuditPayload(
            SalePromotion salePromotion,
            @Nullable Map<String, Object> extraDetails
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("salePromotionId", salePromotion.getId());
        details.put("promotionId", salePromotion.getPromotion() == null ? null : salePromotion.getPromotion().getId());
        details.put("discountApplied", salePromotion.getDiscountApplied());
        details.put("appliedAt", salePromotion.getAppliedAt());
        if (extraDetails != null) {
            details.putAll(extraDetails);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (salePromotion.getTicketSale() != null) {
            payload.put("saleId", salePromotion.getTicketSale().getId());
            if (salePromotion.getTicketSale().getMethod() != null) {
                payload.put("method", salePromotion.getTicketSale().getMethod().name());
            }
            payload.put("amount", salePromotion.getTicketSale().getAmount());
        }
        payload.put("details", details);
        return payload;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.PAYMENT_AUDIT, "payment_audit_trail"));
        }
    }
}
