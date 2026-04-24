package com.team06.eventticketing.sales.controller;

import com.team06.eventticketing.common.cache.CachedDetail;
import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.sales.dto.PromotionRequest;
import com.team06.eventticketing.sales.dto.PromotionResponse;
import com.team06.eventticketing.sales.dto.PromotionUsageDTO;
import com.team06.eventticketing.sales.service.PromotionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public List<PromotionResponse> getAllPromotions() {
        return promotionService.getAllPromotions();
    }

    @GetMapping("/{id}")
    @CachedDetail(service = "sales-service", entity = "promotion", key = "#id", ttlSeconds = 900)
    public PromotionResponse getPromotionById(@PathVariable Long id) {
        return promotionService.getPromotionById(id);
    }

    @GetMapping("/top-used")
    @CachedFeature(service = "sales-service", featureId = "S5-F9", ttlSeconds = 600)
    public List<PromotionUsageDTO> getTopUsedPromotions(@RequestParam int limit) {
        return promotionService.getTopUsedPromotions(limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @InvalidateServiceCaches(service = "sales-service", featurePrefix = "S5-")
    public PromotionResponse createPromotion(@RequestBody PromotionRequest request) {
        return promotionService.createPromotion(request);
    }

    @PutMapping("/{id}")
    @InvalidateServiceCaches(
            service = "sales-service",
            featurePrefix = "S5-",
            detailKeys = {"'sales-service::promotion::' + #id"})
    public PromotionResponse updatePromotion(@PathVariable Long id, @RequestBody PromotionRequest request) {
        return promotionService.updatePromotion(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @InvalidateServiceCaches(
            service = "sales-service",
            featurePrefix = "S5-",
            detailKeys = {"'sales-service::promotion::' + #id"})
    public void deletePromotion(@PathVariable Long id) {
        promotionService.deletePromotion(id);
    }
}
