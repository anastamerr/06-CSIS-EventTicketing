package com.team06.eventticketing.sales.service;

import com.team06.eventticketing.sales.dto.PromotionRequest;
import com.team06.eventticketing.sales.dto.PromotionResponse;
import com.team06.eventticketing.sales.dto.PromotionUsageDTO;
import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.model.PromotionDiscountType;
import com.team06.eventticketing.sales.repository.PromotionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
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
        List<Object[]> results = promotionRepository.findTopUsedPromotions(limit);
        return results.stream()
                .map(this::mapToPromotionUsageDTO)
                .toList();
    }

    @Transactional
    public PromotionResponse createPromotion(PromotionRequest request) {
        Promotion promotion = new Promotion();
        apply(promotion, request);
        return toResponse(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionRequest request) {
        Promotion existing = findPromotion(id);
        apply(existing, request);
        return toResponse(promotionRepository.save(existing));
    }

    @Transactional
    public void deletePromotion(Long id) {
        promotionRepository.delete(findPromotion(id));
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

    private PromotionUsageDTO mapToPromotionUsageDTO(Object[] row) {
        return PromotionUsageDTO.from(
            (Long) row[0],                      // promotionId
            (String) row[1],                    // code
            (PromotionDiscountType) row[2],     // discountType
            (Double) row[3],                    // discountValue
            (Integer) row[4],                   // timesUsed (currentUses)
            (Double) row[5],                    // totalDiscountGiven
            (Boolean) row[6],                   // active
            (LocalDateTime) row[7]              // expiryDate (for computing expired)
        );
    }
}
