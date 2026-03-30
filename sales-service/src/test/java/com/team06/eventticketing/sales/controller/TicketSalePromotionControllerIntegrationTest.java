package com.team06.eventticketing.sales.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class TicketSalePromotionControllerIntegrationTest {

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
    void applyPromotionCreatesJoinAndReturnsUpdatedTicketSale() throws Exception {
        TicketSale ticketSale = ticketSaleRepository.saveAndFlush(newTicketSale(800.0));
        Promotion promotion = promotionRepository.saveAndFlush(newPromotion("SHOW25", PromotionDiscountType.PERCENTAGE, 25.0));

        mockMvc.perform(post("/api/sales/{saleId}/promotions/{promotionId}", ticketSale.getId(), promotion.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketSale.getId()))
                .andExpect(jsonPath("$.bookingId").value(10))
                .andExpect(jsonPath("$.amount").value(800.0))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.salePromotions[0].discountApplied").value(200.0))
                .andExpect(jsonPath("$.salePromotions[0].promotion.code").value("SHOW25"))
                .andExpect(jsonPath("$.salePromotions[0].promotion.discountType").value("PERCENTAGE"));

        org.junit.jupiter.api.Assertions.assertEquals(1, salePromotionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                promotionRepository.findById(promotion.getId()).orElseThrow().getCurrentUses()
        );
    }

    @Test
    void applyPromotionRejectsDuplicatePromotion() throws Exception {
        TicketSale ticketSale = ticketSaleRepository.saveAndFlush(newTicketSale(800.0));
        Promotion promotion = promotionRepository.saveAndFlush(newPromotion("SHOW25", PromotionDiscountType.PERCENTAGE, 25.0));

        SalePromotion existing = new SalePromotion();
        existing.setTicketSale(ticketSale);
        existing.setPromotion(promotion);
        existing.setDiscountApplied(200.0);
        salePromotionRepository.saveAndFlush(existing);

        mockMvc.perform(post("/api/sales/{saleId}/promotions/{promotionId}", ticketSale.getId(), promotion.getId()))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(1, salePromotionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                promotionRepository.findById(promotion.getId()).orElseThrow().getCurrentUses()
        );
    }

    @Test
    void applyPromotionCapsFixedDiscountAtSaleAmount() throws Exception {
        TicketSale ticketSale = ticketSaleRepository.saveAndFlush(newTicketSale(800.0));
        Promotion promotion = promotionRepository.saveAndFlush(newPromotion("BIGSAVE", PromotionDiscountType.FIXED, 9999.0));

        mockMvc.perform(post("/api/sales/{saleId}/promotions/{promotionId}", ticketSale.getId(), promotion.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketSale.getId()))
                .andExpect(jsonPath("$.salePromotions[0].discountApplied").value(800.0))
                .andExpect(jsonPath("$.salePromotions[0].promotion.code").value("BIGSAVE"));
    }

    @Test
    void applyPromotionRejectsInactivePromotion() throws Exception {
        TicketSale ticketSale = ticketSaleRepository.saveAndFlush(newTicketSale(800.0));
        Promotion promotion = newPromotion("SHOW25", PromotionDiscountType.PERCENTAGE, 25.0);
        promotion.setActive(Boolean.FALSE);
        promotion = promotionRepository.saveAndFlush(promotion);

        mockMvc.perform(post("/api/sales/{saleId}/promotions/{promotionId}", ticketSale.getId(), promotion.getId()))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(0, salePromotionRepository.count());
    }

    @Test
    void applyPromotionRejectsCompletedSale() throws Exception {
        TicketSale ticketSale = newTicketSale(800.0);
        ticketSale.setStatus(TicketSaleStatus.COMPLETED);
        ticketSale = ticketSaleRepository.saveAndFlush(ticketSale);
        Promotion promotion = promotionRepository.saveAndFlush(newPromotion("SHOW25", PromotionDiscountType.PERCENTAGE, 25.0));

        mockMvc.perform(post("/api/sales/{saleId}/promotions/{promotionId}", ticketSale.getId(), promotion.getId()))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(0, salePromotionRepository.count());
    }

    private TicketSale newTicketSale(double amount) {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setBookingId(10L);
        ticketSale.setUserId(20L);
        ticketSale.setAmount(amount);
        ticketSale.setMethod(TicketSaleMethod.CREDIT_CARD);
        ticketSale.setStatus(TicketSaleStatus.PENDING);
        ticketSale.setTransactionDetails(new LinkedHashMap<>());
        return ticketSale;
    }

    private Promotion newPromotion(String code, PromotionDiscountType discountType, double discountValue) {
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setDiscountType(discountType);
        promotion.setDiscountValue(discountValue);
        promotion.setMaxUses(3);
        promotion.setCurrentUses(0);
        promotion.setExpiryDate(LocalDateTime.now().plusDays(1));
        promotion.setActive(Boolean.TRUE);
        return promotion;
    }
}
