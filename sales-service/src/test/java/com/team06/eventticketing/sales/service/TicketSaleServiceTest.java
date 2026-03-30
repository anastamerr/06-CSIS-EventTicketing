package com.team06.eventticketing.sales.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.TicketSaleRequest;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.model.PromotionDiscountType;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleMethod;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import java.time.LocalDateTime;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TicketSaleServiceTest {

    @Mock
    private TicketSaleRepository ticketSaleRepository;

    @Captor
    private ArgumentCaptor<TicketSale> ticketSaleCaptor;

    private TicketSaleService ticketSaleService;

    @BeforeEach
    void setUp() {
        ticketSaleService = new TicketSaleService(ticketSaleRepository);
    }

    @Test
    void crudOperationsWork() {
        TicketSale existing = new TicketSale();
        existing.setId(1L);
        existing.setBookingId(10L);
        existing.setUserId(20L);
        existing.setAmount(100.0);
        existing.setMethod(TicketSaleMethod.CREDIT_CARD);
        existing.setStatus(TicketSaleStatus.PENDING);
        existing.setTransactionDetails(Map.of("gatewayResponse", "approved"));

        when(ticketSaleRepository.findAll()).thenReturn(List.of(existing));
        when(ticketSaleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketSaleRequest createRequest = new TicketSaleRequest();
        createRequest.setBookingId(11L);
        createRequest.setUserId(21L);
        createRequest.setAmount(120.0);
        createRequest.setMethod(TicketSaleMethod.DEBIT_CARD);
        createRequest.setStatus(TicketSaleStatus.COMPLETED);
        createRequest.setTransactionDetails(new LinkedHashMap<>(Map.of("gatewayResponse", "approved")));

        assertEquals(1, ticketSaleService.getAllTicketSales().size());

        TicketSaleResponse created = ticketSaleService.createTicketSale(createRequest);
        assertEquals(11L, created.getBookingId());
        assertEquals(21L, created.getUserId());

        TicketSaleRequest updateRequest = new TicketSaleRequest();
        updateRequest.setBookingId(12L);
        updateRequest.setUserId(22L);
        updateRequest.setAmount(130.0);
        updateRequest.setMethod(TicketSaleMethod.WALLET);
        updateRequest.setStatus(TicketSaleStatus.REFUNDED);
        updateRequest.setTransactionDetails(new LinkedHashMap<>(Map.of("gatewayResponse", "refunded")));

        TicketSaleResponse updated = ticketSaleService.updateTicketSale(1L, updateRequest);
        assertEquals(12L, updated.getBookingId());
        assertEquals(TicketSaleStatus.REFUNDED, updated.getStatus());

        TicketSaleResponse found = ticketSaleService.getTicketSaleById(1L);
        assertEquals(1L, found.getId());

        ticketSaleService.deleteTicketSale(1L);
        verify(ticketSaleRepository).delete(existing);
    }

    @Test
    void missingTicketSaleReturns404() {
        when(ticketSaleRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.getTicketSaleById(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getTicketSaleDetailsAggregatesAppliedPromotions() {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setId(7L);
        ticketSale.setBookingId(17L);
        ticketSale.setUserId(27L);
        ticketSale.setAmount(800.0);
        ticketSale.setMethod(TicketSaleMethod.CREDIT_CARD);
        ticketSale.setStatus(TicketSaleStatus.COMPLETED);
        ticketSale.setTransactionDetails(new LinkedHashMap<>(Map.of("gatewayResponse", "approved")));

        Promotion firstPromotion = new Promotion();
        firstPromotion.setCode("PROMO200");
        firstPromotion.setDiscountType(PromotionDiscountType.FIXED);

        SalePromotion firstSalePromotion = new SalePromotion();
        firstSalePromotion.setPromotion(firstPromotion);
        firstSalePromotion.setDiscountApplied(200.0);
        firstSalePromotion.setAppliedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        firstSalePromotion.setTicketSale(ticketSale);

        Promotion secondPromotion = new Promotion();
        secondPromotion.setCode("PROMO100");
        secondPromotion.setDiscountType(PromotionDiscountType.PERCENTAGE);

        SalePromotion secondSalePromotion = new SalePromotion();
        secondSalePromotion.setPromotion(secondPromotion);
        secondSalePromotion.setDiscountApplied(100.0);
        secondSalePromotion.setAppliedAt(LocalDateTime.of(2026, 3, 2, 11, 0));
        secondSalePromotion.setTicketSale(ticketSale);

        ticketSale.setSalePromotions(List.of(secondSalePromotion, firstSalePromotion));

        when(ticketSaleRepository.findByIdWithSalePromotions(7L)).thenReturn(Optional.of(ticketSale));

        SaleDetailsDTO details = ticketSaleService.getTicketSaleDetails(7L);

        assertEquals(7L, details.getSaleId());
        assertEquals(800.0, details.getOriginalAmount());
        assertEquals(2, details.getAppliedPromotions().size());
        assertEquals("PROMO200", details.getAppliedPromotions().get(0).getPromotionCode());
        assertEquals(PromotionDiscountType.FIXED, details.getAppliedPromotions().get(0).getDiscountType());
        assertEquals(300.0, details.getTotalDiscount());
        assertEquals(500.0, details.getFinalAmount());
    }

    @Test
    void getTicketSaleDetailsReturnsOriginalAmountWhenNoPromotionsExist() {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setId(8L);
        ticketSale.setBookingId(18L);
        ticketSale.setUserId(28L);
        ticketSale.setAmount(650.0);
        ticketSale.setMethod(TicketSaleMethod.WALLET);
        ticketSale.setStatus(TicketSaleStatus.PENDING);
        ticketSale.setTransactionDetails(new LinkedHashMap<>());
        ticketSale.setSalePromotions(List.of());

        when(ticketSaleRepository.findByIdWithSalePromotions(8L)).thenReturn(Optional.of(ticketSale));

        SaleDetailsDTO details = ticketSaleService.getTicketSaleDetails(8L);

        assertEquals(0.0, details.getTotalDiscount());
        assertEquals(650.0, details.getFinalAmount());
        assertEquals(0, details.getAppliedPromotions().size());
    }

    @Test
    void getTicketSaleDetailsThrowsNotFoundForUnknownSale() {
        when(ticketSaleRepository.findByIdWithSalePromotions(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.getTicketSaleDetails(404L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
