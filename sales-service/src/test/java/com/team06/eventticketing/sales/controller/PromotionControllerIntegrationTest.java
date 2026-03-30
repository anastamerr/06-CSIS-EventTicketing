package com.team06.eventticketing.sales.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.model.PromotionDiscountType;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleMethod;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.repository.PromotionRepository;
import com.team06.eventticketing.sales.repository.SalePromotionRepository;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PromotionControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketSaleRepository ticketSaleRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private SalePromotionRepository salePromotionRepository;

    @BeforeEach
    void setUp() {
        salePromotionRepository.deleteAll();
        promotionRepository.deleteAll();
        ticketSaleRepository.deleteAll();
    }

    @Test
    void getTopUsedPromotionsReturnsOrderedUsageReport() throws Exception {
        Promotion promoA = createPromotion("PROMO-A", PromotionDiscountType.FIXED, 200.0, LocalDateTime.now().plusDays(10));
        Promotion promoB = createPromotion("PROMO-B", PromotionDiscountType.FIXED, 150.0, LocalDateTime.now().plusDays(10));
        Promotion promoC = createPromotion("PROMO-C", PromotionDiscountType.FIXED, 100.0, LocalDateTime.now().minusDays(1));

        applyPromotion(promoA, 200.0);
        applyPromotion(promoA, 200.0);
        applyPromotion(promoA, 200.0);
        applyPromotion(promoA, 200.0);
        applyPromotion(promoA, 200.0);
        promoA.setCurrentUses(5);
        promotionRepository.saveAndFlush(promoA);

        applyPromotion(promoB, 150.0);
        applyPromotion(promoB, 150.0);
        applyPromotion(promoB, 150.0);
        promoB.setCurrentUses(3);
        promotionRepository.saveAndFlush(promoB);

        applyPromotion(promoC, 100.0);
        promoC.setCurrentUses(1);
        promotionRepository.saveAndFlush(promoC);

        mockMvc.perform(get("/api/sales/promotions/top-used").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].promotionId").value(promoA.getId()))
                .andExpect(jsonPath("$[0].code").value("PROMO-A"))
                .andExpect(jsonPath("$[0].timesUsed").value(5))
                .andExpect(jsonPath("$[0].totalDiscountGiven").value(1000.0))
                .andExpect(jsonPath("$[0].expired").value(false))
                .andExpect(jsonPath("$[1].promotionId").value(promoB.getId()))
                .andExpect(jsonPath("$[1].timesUsed").value(3))
                .andExpect(jsonPath("$[1].totalDiscountGiven").value(450.0));
    }

    @Test
    void getTopUsedPromotionsMarksExpiredPromotions() throws Exception {
        Promotion expiredPromotion = createPromotion(
                "EXPIRED-PROMO",
                PromotionDiscountType.PERCENTAGE,
                10.0,
                LocalDateTime.now().minusDays(1));
        applyPromotion(expiredPromotion, 100.0);
        expiredPromotion.setCurrentUses(1);
        promotionRepository.saveAndFlush(expiredPromotion);

        mockMvc.perform(get("/api/sales/promotions/top-used").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].promotionId").value(expiredPromotion.getId()))
                .andExpect(jsonPath("$[0].expired").value(true));
    }

    @Test
    void getTopUsedPromotionsRejectsNonPositiveLimit() throws Exception {
        mockMvc.perform(get("/api/sales/promotions/top-used").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTopUsedPromotionsReturnsEmptyListWhenNoPromotionsExist() throws Exception {
        mockMvc.perform(get("/api/sales/promotions/top-used").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Promotion createPromotion(String code, PromotionDiscountType discountType, double discountValue,
                                      LocalDateTime expiryDate) {
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setDiscountType(discountType);
        promotion.setDiscountValue(discountValue);
        promotion.setMaxUses(10);
        promotion.setCurrentUses(0);
        promotion.setExpiryDate(expiryDate);
        promotion.setActive(Boolean.TRUE);
        return promotionRepository.saveAndFlush(promotion);
    }

    private void applyPromotion(Promotion promotion, double discountApplied) {
        TicketSale sale = new TicketSale();
        sale.setBookingId(100L);
        sale.setUserId(200L);
        sale.setAmount(1000.0);
        sale.setMethod(TicketSaleMethod.CREDIT_CARD);
        sale.setStatus(TicketSaleStatus.PENDING);
        sale.setTransactionDetails(new LinkedHashMap<>());
        sale = ticketSaleRepository.saveAndFlush(sale);

        SalePromotion salePromotion = new SalePromotion();
        salePromotion.setTicketSale(sale);
        salePromotion.setPromotion(promotion);
        salePromotion.setDiscountApplied(discountApplied);
        salePromotionRepository.saveAndFlush(salePromotion);
    }
}
