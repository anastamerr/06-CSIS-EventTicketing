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
    void getTopUsedPromotionsReturnsPromotionsOrderedByUsage() throws Exception {
        // Create 3 promotions
        Promotion promotionA = createPromotion("PROMO-A", PromotionDiscountType.FIXED, 200.0, 10);
        Promotion promotionB = createPromotion("PROMO-B", PromotionDiscountType.FIXED, 150.0, 10);
        Promotion promotionC = createPromotion("PROMO-C", PromotionDiscountType.FIXED, 100.0, 10);

        // Apply promotion A to 5 sales (total discount = 1000)
        for (int i = 0; i < 5; i++) {
            TicketSale sale = createTicketSale(1000.0);
            applySalePromotion(sale, promotionA, 200.0);
        }
        promotionA.setCurrentUses(5);
        promotionRepository.saveAndFlush(promotionA);

        // Apply promotion B to 3 sales (total discount = 450)
        for (int i = 0; i < 3; i++) {
            TicketSale sale = createTicketSale(1000.0);
            applySalePromotion(sale, promotionB, 150.0);
        }
        promotionB.setCurrentUses(3);
        promotionRepository.saveAndFlush(promotionB);

        // Apply promotion C to 1 sale (total discount = 100)
        TicketSale saleC = createTicketSale(1000.0);
        applySalePromotion(saleC, promotionC, 100.0);
        promotionC.setCurrentUses(1);
        promotionRepository.saveAndFlush(promotionC);

        // GET /api/sales/promotions/top-used?limit=2
        mockMvc.perform(get("/api/sales/promotions/top-used")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].promotionId").value(promotionA.getId()))
                .andExpect(jsonPath("$[0].code").value("PROMO-A"))
                .andExpect(jsonPath("$[0].discountType").value("FIXED"))
                .andExpect(jsonPath("$[0].discountValue").value(200.0))
                .andExpect(jsonPath("$[0].timesUsed").value(5))
                .andExpect(jsonPath("$[0].totalDiscountGiven").value(1000.0))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].expired").value(false))
                .andExpect(jsonPath("$[1].promotionId").value(promotionB.getId()))
                .andExpect(jsonPath("$[1].code").value("PROMO-B"))
                .andExpect(jsonPath("$[1].timesUsed").value(3))
                .andExpect(jsonPath("$[1].totalDiscountGiven").value(450.0))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[1].expired").value(false))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getTopUsedPromotionsCorrectlyIdentifiesExpiredPromotions() throws Exception {
        // Create an expired promotion
        Promotion expiredPromotion = createExpiredPromotion("EXPIRED-PROMO", PromotionDiscountType.PERCENTAGE, 10.0, 5);

        // Apply the expired promotion to a sale
        TicketSale sale = createTicketSale(1000.0);
        applySalePromotion(sale, expiredPromotion, 100.0);
        expiredPromotion.setCurrentUses(1);
        promotionRepository.saveAndFlush(expiredPromotion);

        // GET /api/sales/promotions/top-used?limit=1
        mockMvc.perform(get("/api/sales/promotions/top-used")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].promotionId").value(expiredPromotion.getId()))
                .andExpect(jsonPath("$[0].code").value("EXPIRED-PROMO"))
                .andExpect(jsonPath("$[0].expired").value(true));
    }

    @Test
    void getTopUsedPromotionsHandlesPromotionsWithNoUsage() throws Exception {
        // Create a promotion with no sales
        Promotion unusedPromotion = createPromotion("UNUSED", PromotionDiscountType.FIXED, 50.0, 10);
        unusedPromotion.setCurrentUses(0);
        promotionRepository.saveAndFlush(unusedPromotion);

        // GET /api/sales/promotions/top-used?limit=1
        mockMvc.perform(get("/api/sales/promotions/top-used")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].promotionId").value(unusedPromotion.getId()))
                .andExpect(jsonPath("$[0].code").value("UNUSED"))
                .andExpect(jsonPath("$[0].timesUsed").value(0))
                .andExpect(jsonPath("$[0].totalDiscountGiven").value(0.0));
    }

    private Promotion createPromotion(String code, PromotionDiscountType discountType, double discountValue, int maxUses) {
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setDiscountType(discountType);
        promotion.setDiscountValue(discountValue);
        promotion.setMaxUses(maxUses);
        promotion.setCurrentUses(0);
        promotion.setExpiryDate(LocalDateTime.now().plusDays(30));
        promotion.setActive(Boolean.TRUE);
        return promotionRepository.saveAndFlush(promotion);
    }

    private Promotion createExpiredPromotion(String code, PromotionDiscountType discountType, double discountValue, int maxUses) {
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setDiscountType(discountType);
        promotion.setDiscountValue(discountValue);
        promotion.setMaxUses(maxUses);
        promotion.setCurrentUses(0);
        promotion.setExpiryDate(LocalDateTime.now().minusDays(1));
        promotion.setActive(Boolean.TRUE);
        return promotionRepository.saveAndFlush(promotion);
    }

    private TicketSale createTicketSale(double amount) {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setBookingId(100L);
        ticketSale.setUserId(200L);
        ticketSale.setAmount(amount);
        ticketSale.setMethod(TicketSaleMethod.CREDIT_CARD);
        ticketSale.setStatus(TicketSaleStatus.PENDING);
        ticketSale.setTransactionDetails(new LinkedHashMap<>());
        return ticketSaleRepository.saveAndFlush(ticketSale);
    }

    private void applySalePromotion(TicketSale ticketSale, Promotion promotion, double discountApplied) {
        SalePromotion salePromotion = new SalePromotion();
        salePromotion.setTicketSale(ticketSale);
        salePromotion.setPromotion(promotion);
        salePromotion.setDiscountApplied(discountApplied);
        salePromotionRepository.saveAndFlush(salePromotion);
    }
}
