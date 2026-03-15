package com.team06.eventticketing.sales.repository;

import com.team06.eventticketing.sales.model.SalePromotion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalePromotionRepository extends JpaRepository<SalePromotion, Long> {

    List<SalePromotion> findByTicketSaleId(Long ticketSaleId);

    List<SalePromotion> findByPromotionId(Long promotionId);
}
