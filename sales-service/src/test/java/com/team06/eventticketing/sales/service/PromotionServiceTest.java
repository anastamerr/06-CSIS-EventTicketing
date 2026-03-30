package com.team06.eventticketing.sales.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.sales.dto.PromotionUsageDTO;
import com.team06.eventticketing.sales.dto.PromotionRequest;
import com.team06.eventticketing.sales.dto.PromotionResponse;
import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.model.PromotionDiscountType;
import com.team06.eventticketing.sales.repository.PromotionRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Captor
    private ArgumentCaptor<Promotion> promotionCaptor;

    private PromotionService promotionService;

    @BeforeEach
    void setUp() {
        promotionService = new PromotionService(promotionRepository);
    }

    @Test
    void crudOperationsWork() {
        Promotion existing = new Promotion();
        existing.setId(1L);
        existing.setCode("PROMO10");
        existing.setDiscountType(PromotionDiscountType.PERCENTAGE);
        existing.setDiscountValue(10.0);
        existing.setMaxUses(100);
        existing.setCurrentUses(1);
        existing.setExpiryDate(LocalDateTime.now().plusDays(10));
        existing.setActive(Boolean.TRUE);
        existing.setMetadata(Map.of("scope", "all"));

        when(promotionRepository.findAll()).thenReturn(List.of(existing));
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(promotionRepository.save(promotionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionRequest createRequest = new PromotionRequest();
        createRequest.setCode("PROMO20");
        createRequest.setDiscountType(PromotionDiscountType.FIXED);
        createRequest.setDiscountValue(20.0);
        createRequest.setMaxUses(50);
        createRequest.setCurrentUses(0);
        createRequest.setExpiryDate(LocalDateTime.now().plusDays(20));
        createRequest.setActive(Boolean.TRUE);
        createRequest.setMetadata(new LinkedHashMap<>(Map.of("scope", "vip")));

        assertEquals(1, promotionService.getAllPromotions().size());

        PromotionResponse created = promotionService.createPromotion(createRequest);
        assertEquals("PROMO20", created.getCode());

        PromotionRequest updateRequest = new PromotionRequest();
        updateRequest.setCode("PROMO30");
        updateRequest.setDiscountType(PromotionDiscountType.FIXED);
        updateRequest.setDiscountValue(30.0);
        updateRequest.setMaxUses(60);
        updateRequest.setCurrentUses(5);
        updateRequest.setExpiryDate(LocalDateTime.now().plusDays(30));
        updateRequest.setActive(Boolean.FALSE);
        updateRequest.setMetadata(new LinkedHashMap<>(Map.of("scope", "students")));

        PromotionResponse updated = promotionService.updatePromotion(1L, updateRequest);
        assertEquals("PROMO30", updated.getCode());
        assertEquals(Boolean.FALSE, updated.getActive());

        PromotionResponse found = promotionService.getPromotionById(1L);
        assertEquals(1L, found.getId());

        promotionService.deletePromotion(1L);
        verify(promotionRepository).delete(existing);
    }

    @Test
    void missingPromotionReturns404() {
        when(promotionRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> promotionService.getPromotionById(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getTopUsedPromotionsMapsAggregationAndComputesExpired() {
        LocalDateTime expiredDate = LocalDateTime.now().minusDays(1);
        when(promotionRepository.findTopUsedPromotions(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{
                        1L,
                        "SHOW25",
                        PromotionDiscountType.FIXED,
                        25.0,
                        3,
                        550.0,
                        Boolean.TRUE,
                        expiredDate
                }));

        List<PromotionUsageDTO> result = promotionService.getTopUsedPromotions(2);

        assertEquals(1, result.size());
        assertEquals(1L, result.getFirst().promotionId());
        assertEquals("SHOW25", result.getFirst().code());
        assertEquals(3, result.getFirst().timesUsed());
        assertEquals(550.0, result.getFirst().totalDiscountGiven());
        assertEquals(Boolean.TRUE, result.getFirst().expired());
    }

    @Test
    void getTopUsedPromotionsRejectsNonPositiveLimit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> promotionService.getTopUsedPromotions(0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
