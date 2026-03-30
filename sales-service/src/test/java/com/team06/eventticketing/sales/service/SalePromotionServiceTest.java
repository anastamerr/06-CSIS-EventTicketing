package com.team06.eventticketing.sales.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.sales.dto.SalePromotionRequest;
import com.team06.eventticketing.sales.dto.SalePromotionResponse;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SalePromotionServiceTest {

    @Mock
    private SalePromotionRepository salePromotionRepository;

    @Mock
    private TicketSaleRepository ticketSaleRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Captor
    private ArgumentCaptor<SalePromotion> salePromotionCaptor;

    private SalePromotionService salePromotionService;

    @BeforeEach
    void setUp() {
        salePromotionService = new SalePromotionService(
                salePromotionRepository,
                ticketSaleRepository,
                promotionRepository
        );
    }

    @Test
    void crudOperationsWork() {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setId(1L);
        ticketSale.setBookingId(10L);
        ticketSale.setUserId(20L);
        ticketSale.setAmount(100.0);
        ticketSale.setMethod(TicketSaleMethod.CREDIT_CARD);
        ticketSale.setStatus(TicketSaleStatus.COMPLETED);
        ticketSale.setTransactionDetails(new LinkedHashMap<>());

        Promotion promotion = new Promotion();
        promotion.setId(2L);
        promotion.setCode("PROMO10");
        promotion.setDiscountType(PromotionDiscountType.PERCENTAGE);
        promotion.setDiscountValue(10.0);
        promotion.setMaxUses(100);
        promotion.setCurrentUses(0);
        promotion.setExpiryDate(LocalDateTime.now().plusDays(10));
        promotion.setActive(Boolean.TRUE);

        SalePromotion existing = new SalePromotion();
        existing.setId(3L);
        existing.setTicketSale(ticketSale);
        existing.setPromotion(promotion);
        existing.setDiscountApplied(10.0);
        existing.setAppliedAt(LocalDateTime.now());

        when(salePromotionRepository.findAll()).thenReturn(List.of(existing));
        when(salePromotionRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(ticketSaleRepository.findById(1L)).thenReturn(Optional.of(ticketSale));
        when(promotionRepository.findById(2L)).thenReturn(Optional.of(promotion));
        when(salePromotionRepository.save(salePromotionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        SalePromotionRequest createRequest = new SalePromotionRequest();
        createRequest.setTicketSaleId(1L);
        createRequest.setPromotionId(2L);
        createRequest.setDiscountApplied(10.0);
        createRequest.setAppliedAt(LocalDateTime.now());

        assertEquals(1, salePromotionService.getAllSalePromotions().size());

        SalePromotionResponse created = salePromotionService.createSalePromotion(createRequest);
        assertEquals(1L, created.getTicketSaleId());
        assertEquals(2L, created.getPromotionId());

        SalePromotionRequest updateRequest = new SalePromotionRequest();
        updateRequest.setTicketSaleId(1L);
        updateRequest.setPromotionId(2L);
        updateRequest.setDiscountApplied(12.0);
        updateRequest.setAppliedAt(LocalDateTime.now().plusDays(1));

        SalePromotionResponse updated = salePromotionService.updateSalePromotion(3L, updateRequest);
        assertEquals(12.0, updated.getDiscountApplied());

        SalePromotionResponse found = salePromotionService.getSalePromotionById(3L);
        assertEquals(3L, found.getId());

        salePromotionService.deleteSalePromotion(3L);
        verify(salePromotionRepository).delete(existing);
    }

    @Test
    void missingReferencedTicketSaleReturns404() {
        SalePromotionRequest request = new SalePromotionRequest();
        request.setTicketSaleId(99L);
        request.setPromotionId(2L);
        request.setDiscountApplied(10.0);

        when(ticketSaleRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> salePromotionService.createSalePromotion(request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void applyPromotionToTicketSaleCreatesJoinAndIncrementsUses() {
        TicketSale ticketSale = ticketSale(1L, 800.0, TicketSaleStatus.PENDING);
        Promotion promotion = promotion(2L, PromotionDiscountType.PERCENTAGE, 25.0, 3, 0, true);

        when(ticketSaleRepository.findByIdWithSalePromotionsForUpdate(1L)).thenReturn(Optional.of(ticketSale));
        when(ticketSaleRepository.findByIdWithSalePromotions(1L)).thenReturn(Optional.of(ticketSale));
        when(promotionRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(promotion));
        when(salePromotionRepository.existsByTicketSaleIdAndPromotionId(1L, 2L)).thenReturn(false);
        when(salePromotionRepository.saveAndFlush(salePromotionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketSale result = salePromotionService.applyPromotionToTicketSale(1L, 2L);

        SalePromotion savedPromotion = salePromotionCaptor.getValue();
        assertEquals(200.0, savedPromotion.getDiscountApplied());
        assertSame(ticketSale, savedPromotion.getTicketSale());
        assertSame(promotion, savedPromotion.getPromotion());
        assertEquals(1, promotion.getCurrentUses());
        assertSame(ticketSale, result);
        assertEquals(1, ticketSale.getSalePromotions().size());
        assertSame(savedPromotion, ticketSale.getSalePromotions().get(0));
        verify(promotionRepository).save(promotion);
    }

    @Test
    void applyPromotionToTicketSaleCapsFixedDiscountAtSaleAmount() {
        TicketSale ticketSale = ticketSale(1L, 80.0, TicketSaleStatus.PENDING);
        Promotion promotion = promotion(2L, PromotionDiscountType.FIXED, 100.0, 5, 0, true);

        when(ticketSaleRepository.findByIdWithSalePromotionsForUpdate(1L)).thenReturn(Optional.of(ticketSale));
        when(ticketSaleRepository.findByIdWithSalePromotions(1L)).thenReturn(Optional.of(ticketSale));
        when(promotionRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(promotion));
        when(salePromotionRepository.existsByTicketSaleIdAndPromotionId(1L, 2L)).thenReturn(false);
        when(salePromotionRepository.saveAndFlush(salePromotionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        salePromotionService.applyPromotionToTicketSale(1L, 2L);

        assertEquals(80.0, salePromotionCaptor.getValue().getDiscountApplied());
    }

    @Test
    void applyPromotionToTicketSaleRejectsNonPendingSale() {
        TicketSale ticketSale = ticketSale(1L, 800.0, TicketSaleStatus.COMPLETED);

        when(ticketSaleRepository.findByIdWithSalePromotionsForUpdate(1L)).thenReturn(Optional.of(ticketSale));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> salePromotionService.applyPromotionToTicketSale(1L, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(promotionRepository, never()).findByIdForUpdate(any());
        verify(salePromotionRepository, never()).saveAndFlush(any());
    }

    @Test
    void applyPromotionToTicketSaleRejectsExpiredPromotion() {
        TicketSale ticketSale = ticketSale(1L, 800.0, TicketSaleStatus.PENDING);
        Promotion promotion = promotion(2L, PromotionDiscountType.PERCENTAGE, 20.0, 3, 0, true);
        promotion.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        when(ticketSaleRepository.findByIdWithSalePromotionsForUpdate(1L)).thenReturn(Optional.of(ticketSale));
        when(promotionRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(promotion));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> salePromotionService.applyPromotionToTicketSale(1L, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(salePromotionRepository, never()).saveAndFlush(any());
    }

    @Test
    void applyPromotionToTicketSaleRejectsInactivePromotion() {
        TicketSale ticketSale = ticketSale(1L, 800.0, TicketSaleStatus.PENDING);
        Promotion promotion = promotion(2L, PromotionDiscountType.PERCENTAGE, 20.0, 3, 0, false);

        when(ticketSaleRepository.findByIdWithSalePromotionsForUpdate(1L)).thenReturn(Optional.of(ticketSale));
        when(promotionRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(promotion));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> salePromotionService.applyPromotionToTicketSale(1L, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(salePromotionRepository, never()).saveAndFlush(any());
        verify(promotionRepository, never()).save(any());
    }

    @Test
    void applyPromotionToTicketSaleRejectsDuplicatePromotion() {
        TicketSale ticketSale = ticketSale(1L, 800.0, TicketSaleStatus.PENDING);
        Promotion promotion = promotion(2L, PromotionDiscountType.PERCENTAGE, 25.0, 3, 0, true);

        when(ticketSaleRepository.findByIdWithSalePromotionsForUpdate(1L)).thenReturn(Optional.of(ticketSale));
        when(promotionRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(promotion));
        when(salePromotionRepository.existsByTicketSaleIdAndPromotionId(1L, 2L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> salePromotionService.applyPromotionToTicketSale(1L, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(salePromotionRepository, never()).saveAndFlush(any());
        verify(promotionRepository, never()).save(any());
    }

    @Test
    void applyPromotionToTicketSaleRejectsUsageLimitReached() {
        TicketSale ticketSale = ticketSale(1L, 800.0, TicketSaleStatus.PENDING);
        Promotion promotion = promotion(2L, PromotionDiscountType.PERCENTAGE, 25.0, 1, 1, true);

        when(ticketSaleRepository.findByIdWithSalePromotionsForUpdate(1L)).thenReturn(Optional.of(ticketSale));
        when(promotionRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(promotion));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> salePromotionService.applyPromotionToTicketSale(1L, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(salePromotionRepository, never()).saveAndFlush(any());
        verify(promotionRepository, never()).save(any());
    }

    private TicketSale ticketSale(Long id, double amount, TicketSaleStatus status) {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setId(id);
        ticketSale.setBookingId(10L);
        ticketSale.setUserId(20L);
        ticketSale.setAmount(amount);
        ticketSale.setMethod(TicketSaleMethod.CREDIT_CARD);
        ticketSale.setStatus(status);
        ticketSale.setTransactionDetails(new LinkedHashMap<>());
        return ticketSale;
    }

    private Promotion promotion(
            Long id,
            PromotionDiscountType discountType,
            double discountValue,
            int maxUses,
            int currentUses,
            boolean active
    ) {
        Promotion promotion = new Promotion();
        promotion.setId(id);
        promotion.setCode("SHOW25");
        promotion.setDiscountType(discountType);
        promotion.setDiscountValue(discountValue);
        promotion.setMaxUses(maxUses);
        promotion.setCurrentUses(currentUses);
        promotion.setExpiryDate(LocalDateTime.now().plusDays(1));
        promotion.setActive(active);
        return promotion;
    }
}
