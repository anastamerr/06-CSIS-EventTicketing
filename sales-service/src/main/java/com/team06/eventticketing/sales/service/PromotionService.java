package com.team06.eventticketing.sales.service;

import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.sales.adapter.PromotionUsageAdapter;
import com.team06.eventticketing.sales.dto.PromotionRequest;
import com.team06.eventticketing.sales.dto.PromotionResponse;
import com.team06.eventticketing.sales.dto.PromotionUsageDTO;
import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.repository.PromotionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionUsageAdapter promotionUsageAdapter;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    public PromotionService(
            PromotionRepository promotionRepository,
            PromotionUsageAdapter promotionUsageAdapter,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.promotionRepository = promotionRepository;
        this.promotionUsageAdapter = promotionUsageAdapter;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
        this.promotionUsageAdapter = new PromotionUsageAdapter();
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> getAllPromotions() {
        return promotionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(Long id) {
        return toResponse(findPromotion(id));
    }

    @Transactional(readOnly = true)
    public List<PromotionUsageDTO> getTopUsedPromotions(int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be greater than zero");
        }

        return promotionRepository.findTopUsedPromotions(PageRequest.of(0, limit)).stream()
                .map(promotionUsageAdapter::adapt)
                .toList();
    }

    @Transactional
    public PromotionResponse createPromotion(PromotionRequest request) {
        Promotion promotion = new Promotion();
        apply(promotion, request);
        Promotion saved = promotionRepository.save(promotion);
        notifyObservers("PROMOTION_CREATED", buildAuditPayload(saved));
        return toResponse(saved);
    }

    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionRequest request) {
        Promotion existing = findPromotion(id);
        apply(existing, request);
        Promotion saved = promotionRepository.save(existing);
        notifyObservers("PROMOTION_UPDATED", buildAuditPayload(saved));
        return toResponse(saved);
    }

    @Transactional
    public void deletePromotion(Long id) {
        Promotion promotion = findPromotion(id);
        promotionRepository.delete(promotion);
        notifyObservers("PROMOTION_DELETED", buildAuditPayload(promotion));
    }

    private Promotion findPromotion(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found"));
    }

    private void apply(Promotion promotion, PromotionRequest request) {
        promotion.setCode(request.getCode());
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(request.getDiscountValue());
        promotion.setMaxUses(request.getMaxUses());
        promotion.setCurrentUses(request.getCurrentUses());
        promotion.setExpiryDate(request.getExpiryDate());
        promotion.setActive(request.getActive());
        promotion.setMetadata(request.getMetadata());
    }

    private PromotionResponse toResponse(Promotion promotion) {
        PromotionResponse response = new PromotionResponse();
        response.setId(promotion.getId());
        response.setCode(promotion.getCode());
        response.setDiscountType(promotion.getDiscountType());
        response.setDiscountValue(promotion.getDiscountValue());
        response.setMaxUses(promotion.getMaxUses());
        response.setCurrentUses(promotion.getCurrentUses());
        response.setExpiryDate(promotion.getExpiryDate());
        response.setActive(promotion.getActive());
        response.setMetadata(promotion.getMetadata());
        return response;
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

    private Map<String, Object> buildAuditPayload(Promotion promotion) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("promotionId", promotion.getId());
        details.put("code", promotion.getCode());
        details.put("discountType", promotion.getDiscountType() == null ? null : promotion.getDiscountType().name());
        details.put("discountValue", promotion.getDiscountValue());
        details.put("currentUses", promotion.getCurrentUses());
        details.put("active", promotion.getActive());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("details", details);
        return payload;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.PAYMENT_AUDIT, "payment_audit_trail"));
        }
    }
}
