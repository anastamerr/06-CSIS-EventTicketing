package com.team06.eventticketing.sales.controller;

import com.team06.eventticketing.sales.dto.SalePromotionRequest;
import com.team06.eventticketing.sales.dto.SalePromotionResponse;
import com.team06.eventticketing.sales.service.SalePromotionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales/sale-promotions")
public class SalePromotionController {

    private final SalePromotionService salePromotionService;

    public SalePromotionController(SalePromotionService salePromotionService) {
        this.salePromotionService = salePromotionService;
    }

    @GetMapping
    public List<SalePromotionResponse> getAllSalePromotions() {
        return salePromotionService.getAllSalePromotions();
    }

    @GetMapping("/{id}")
    public SalePromotionResponse getSalePromotionById(@PathVariable Long id) {
        return salePromotionService.getSalePromotionById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalePromotionResponse createSalePromotion(@RequestBody SalePromotionRequest request) {
        return salePromotionService.createSalePromotion(request);
    }

    @PutMapping("/{id}")
    public SalePromotionResponse updateSalePromotion(@PathVariable Long id, @RequestBody SalePromotionRequest request) {
        return salePromotionService.updateSalePromotion(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSalePromotion(@PathVariable Long id) {
        salePromotionService.deleteSalePromotion(id);
    }
}
