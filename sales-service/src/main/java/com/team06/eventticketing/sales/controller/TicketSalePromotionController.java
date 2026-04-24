package com.team06.eventticketing.sales.controller;

import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.service.SalePromotionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class TicketSalePromotionController {

    private final SalePromotionService salePromotionService;

    public TicketSalePromotionController(SalePromotionService salePromotionService) {
        this.salePromotionService = salePromotionService;
    }

    @PostMapping("/{saleId}/promotions/{promotionId}")
    @InvalidateServiceCaches(
            service = "sales-service",
            featurePrefix = "S5-",
            detailKeys = {
                    "'sales-service::ticket-sale::' + #saleId",
                    "'sales-service::promotion::' + #promotionId"
            })
    public TicketSale applyPromotionToTicketSale(
            @PathVariable Long saleId,
            @PathVariable Long promotionId
    ) {
        return salePromotionService.applyPromotionToTicketSale(saleId, promotionId);
    }
}
