package com.team06.eventticketing.sales.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team06.eventticketing.sales.dto.ProcessBookingSaleRequest;
import com.team06.eventticketing.sales.dto.RevenueReportDTO;
import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.TicketSaleRequest;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.model.PromotionDiscountType;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleMethod;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.repository.BookingJdbcRepository;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import com.team06.eventticketing.sales.repository.UserJdbcRepository;
import java.time.LocalDate;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TicketSaleServiceTest {

    @Mock
    private TicketSaleRepository ticketSaleRepository;

    @Mock
    private BookingJdbcRepository bookingJdbcRepository;

    @Mock
    private UserJdbcRepository userJdbcRepository;

    @Captor
    private ArgumentCaptor<TicketSale> ticketSaleCaptor;

    private TicketSaleService ticketSaleService;

    @BeforeEach
    void setUp() {
        ticketSaleService = new TicketSaleService(ticketSaleRepository, bookingJdbcRepository, userJdbcRepository);
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

    @Test
    void processBookingSaleCompletesPendingSaleAndPopulatesJsonb() {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setId(12L);
        ticketSale.setBookingId(44L);
        ticketSale.setUserId(91L);
        ticketSale.setAmount(300.0);
        ticketSale.setMethod(TicketSaleMethod.WALLET);
        ticketSale.setStatus(TicketSaleStatus.PENDING);
        ticketSale.setTransactionDetails(new LinkedHashMap<>(Map.of("bookingTotalAmount", 650.0)));

        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.CREDIT_CARD);
        request.setCardLastFour("4242");

        when(bookingJdbcRepository.findByIdForUpdate(44L))
                .thenReturn(Optional.of(new BookingJdbcRepository.BookingPaymentRow(44L, "COMPLETED", 650.0)));
        when(ticketSaleRepository.existsByBookingIdAndStatus(44L, TicketSaleStatus.COMPLETED)).thenReturn(false);
        when(ticketSaleRepository.findByBookingIdForUpdate(44L)).thenReturn(List.of(ticketSale));
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketSaleResponse response = ticketSaleService.processBookingSale(44L, request);

        TicketSale saved = ticketSaleCaptor.getValue();
        assertEquals(TicketSaleStatus.COMPLETED, saved.getStatus());
        assertEquals(TicketSaleMethod.CREDIT_CARD, saved.getMethod());
        assertEquals(650.0, saved.getAmount());
        assertEquals("CREDIT_CARD", saved.getTransactionDetails().get("paymentMethod"));
        assertEquals("4242", saved.getTransactionDetails().get("cardLastFour"));
        assertEquals(650.0, saved.getTransactionDetails().get("bookingTotalAmount"));
        assertEquals(TicketSaleStatus.COMPLETED, response.getStatus());
        assertEquals(TicketSaleMethod.CREDIT_CARD, response.getMethod());
        assertEquals(650.0, response.getAmount());
    }

    @Test
    void processBookingSaleRejectsMissingBooking() {
        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.CREDIT_CARD);

        when(bookingJdbcRepository.findByIdForUpdate(44L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.processBookingSale(44L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void processBookingSaleRejectsNonCompletedBooking() {
        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.CREDIT_CARD);

        when(bookingJdbcRepository.findByIdForUpdate(44L))
                .thenReturn(Optional.of(new BookingJdbcRepository.BookingPaymentRow(44L, "PENDING", 650.0)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.processBookingSale(44L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void processBookingSaleRejectsAlreadyPaidBooking() {
        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.WALLET);

        when(bookingJdbcRepository.findByIdForUpdate(44L))
                .thenReturn(Optional.of(new BookingJdbcRepository.BookingPaymentRow(44L, "COMPLETED", 650.0)));
        when(ticketSaleRepository.existsByBookingIdAndStatus(44L, TicketSaleStatus.COMPLETED)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.processBookingSale(44L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("already paid", exception.getReason());
    }

    @Test
    void processBookingSaleRejectsBookingWithoutPendingSale() {
        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.WALLET);

        when(bookingJdbcRepository.findByIdForUpdate(44L))
                .thenReturn(Optional.of(new BookingJdbcRepository.BookingPaymentRow(44L, "COMPLETED", 650.0)));
        when(ticketSaleRepository.existsByBookingIdAndStatus(44L, TicketSaleStatus.COMPLETED)).thenReturn(false);
        when(ticketSaleRepository.findByBookingIdForUpdate(44L)).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.processBookingSale(44L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void retryFailedSaleMarksCompletedAndUpdatesJsonb() {
        TicketSale sale = new TicketSale();
        sale.setId(9L);
        sale.setBookingId(19L);
        sale.setUserId(29L);
        sale.setAmount(250.0);
        sale.setMethod(TicketSaleMethod.CREDIT_CARD);
        sale.setStatus(TicketSaleStatus.FAILED);
        sale.setTransactionDetails(new LinkedHashMap<>(Map.of(
                "gatewayResponse", "declined",
                "cardLastFour", "4242",
                "retryAttempt", 0,
                "failureReason", "card expired"
        )));

        when(ticketSaleRepository.findById(9L)).thenReturn(Optional.of(sale));
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketSale updated = ticketSaleService.retryFailedSale(9L);

        assertEquals(TicketSaleStatus.COMPLETED, updated.getStatus());
        assertEquals(1, updated.getTransactionDetails().get("retryAttempt"));
        assertEquals("approved", updated.getTransactionDetails().get("gatewayResponse"));
        assertEquals("4242", updated.getTransactionDetails().get("cardLastFour"));
        assertEquals("card expired", updated.getTransactionDetails().get("failureReason"));
        verify(ticketSaleRepository).save(sale);
    }

    @Test
    void retryFailedSaleRejectsNonFailedStatus() {
        TicketSale sale = new TicketSale();
        sale.setId(10L);
        sale.setStatus(TicketSaleStatus.COMPLETED);

        when(ticketSaleRepository.findById(10L)).thenReturn(Optional.of(sale));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.retryFailedSale(10L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void retryFailedSaleThrowsNotFoundForUnknownSale() {
        when(ticketSaleRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.retryFailedSale(404L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void refundTicketSaleMarksSaleRefundedAndMergesTransactionDetails() {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setId(30L);
        ticketSale.setStatus(TicketSaleStatus.COMPLETED);
        ticketSale.setTransactionDetails(new LinkedHashMap<>(Map.of(
                "gatewayResponse", "approved",
                "cardLastFour", "4242"
        )));

        RefundRequest request = new RefundRequest();
        request.setReason("event cancelled");

        when(ticketSaleRepository.findById(30L)).thenReturn(Optional.of(ticketSale));
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketSale refunded = ticketSaleService.refundTicketSale(30L, request);

        assertEquals(TicketSaleStatus.REFUNDED, refunded.getStatus());
        assertEquals("approved", refunded.getTransactionDetails().get("gatewayResponse"));
        assertEquals("4242", refunded.getTransactionDetails().get("cardLastFour"));
        assertEquals("event cancelled", refunded.getTransactionDetails().get("refundReason"));
        verify(ticketSaleRepository).save(ticketSale);
    }

    @Test
    void refundTicketSaleRejectsNonCompletedSale() {
        TicketSale ticketSale = new TicketSale();
        ticketSale.setId(31L);
        ticketSale.setStatus(TicketSaleStatus.FAILED);

        RefundRequest request = new RefundRequest();
        request.setReason("event cancelled");

        when(ticketSaleRepository.findById(31L)).thenReturn(Optional.of(ticketSale));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.refundTicketSale(31L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void refundTicketSaleThrowsNotFoundForUnknownSale() {
        RefundRequest request = new RefundRequest();
        request.setReason("event cancelled");

        when(ticketSaleRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.refundTicketSale(404L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getRevenueReportAggregatesCompletedAndRefundedSales() {
        TicketSale completedOne = sale(1L, 200.0, TicketSaleStatus.COMPLETED);
        TicketSale completedTwo = sale(2L, 400.0, TicketSaleStatus.COMPLETED);
        TicketSale completedThree = sale(3L, 600.0, TicketSaleStatus.COMPLETED);
        TicketSale completedFour = sale(4L, 800.0, TicketSaleStatus.COMPLETED);
        TicketSale completedFive = sale(5L, 1000.0, TicketSaleStatus.COMPLETED);
        TicketSale refundedOne = sale(6L, 300.0, TicketSaleStatus.REFUNDED);
        TicketSale refundedTwo = sale(7L, 500.0, TicketSaleStatus.REFUNDED);

        when(ticketSaleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 1).atStartOfDay()
        )).thenReturn(List.of(
                completedOne,
                completedTwo,
                completedThree,
                completedFour,
                completedFive,
                refundedOne,
                refundedTwo
        ));

        RevenueReportDTO report = ticketSaleService.getRevenueReport(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(3000.0, report.getTotalRevenue());
        assertEquals(5L, report.getTotalTransactions());
        assertEquals(600.0, report.getAverageSale());
        assertEquals(800.0, report.getRefundedAmount());
        assertEquals(2L, report.getRefundCount());
    }

    @Test
    void getRevenueReportUsesEndDateAsExclusiveUpperBound() {
        when(ticketSaleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 1).atStartOfDay()
        )).thenReturn(List.of());

        RevenueReportDTO report = ticketSaleService.getRevenueReport(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(0.0, report.getTotalRevenue());
        assertEquals(0L, report.getTotalTransactions());
        assertEquals(0.0, report.getAverageSale());
        assertEquals(0.0, report.getRefundedAmount());
        assertEquals(0L, report.getRefundCount());
    }

    @Test
    void getRevenueReportRejectsStartDateAfterEndDate() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.getRevenueReport(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 31)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getUserSaleSummaryAggregatesCompletedSalesByMethod() {
        when(userJdbcRepository.existsById(55L)).thenReturn(true);
        when(ticketSaleRepository.getCompletedSalesSummaryByMethod(55L)).thenReturn(List.of(
                paymentMethodSummary("CREDIT_CARD", 2L, 800.0),
                paymentMethodSummary("DEBIT_CARD", 1L, 200.0),
                paymentMethodSummary("WALLET", 1L, 150.0)
        ));

        UserSaleSummaryDTO summary = ticketSaleService.getUserSaleSummary(55L);

        assertEquals(55L, summary.getUserId());
        assertEquals(4L, summary.getTotalSales());
        assertEquals(1150.0, summary.getTotalAmount());
        assertEquals(800.0, summary.getMethodBreakdown().get("CREDIT_CARD"));
        assertEquals(200.0, summary.getMethodBreakdown().get("DEBIT_CARD"));
        assertEquals(150.0, summary.getMethodBreakdown().get("WALLET"));
    }

    @Test
    void getUserSaleSummaryReturnsZerosWhenUserHasNoCompletedSales() {
        when(userJdbcRepository.existsById(77L)).thenReturn(true);
        when(ticketSaleRepository.getCompletedSalesSummaryByMethod(77L)).thenReturn(List.of());

        UserSaleSummaryDTO summary = ticketSaleService.getUserSaleSummary(77L);

        assertEquals(77L, summary.getUserId());
        assertEquals(0L, summary.getTotalSales());
        assertEquals(0.0, summary.getTotalAmount());
        assertEquals(Map.of(), summary.getMethodBreakdown());
    }

    @Test
    void getUserSaleSummaryRejectsUnknownUser() {
        when(userJdbcRepository.existsById(999L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.getUserSaleSummary(999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(ticketSaleRepository, never()).getCompletedSalesSummaryByMethod(999L);
    }

    private TicketSale sale(Long id, double amount, TicketSaleStatus status) {
        TicketSale sale = new TicketSale();
        sale.setId(id);
        sale.setBookingId(100L + id);
        sale.setUserId(200L + id);
        sale.setAmount(amount);
        sale.setMethod(TicketSaleMethod.CREDIT_CARD);
        sale.setStatus(status);
        sale.setTransactionDetails(new LinkedHashMap<>());
        return sale;
    }

    private TicketSaleRepository.PaymentMethodSummaryProjection paymentMethodSummary(
            String method,
            Long saleCount,
            Double totalAmount
    ) {
        return new TicketSaleRepository.PaymentMethodSummaryProjection() {
            @Override
            public String getMethod() {
                return method;
            }

            @Override
            public Long getSaleCount() {
                return saleCount;
            }

            @Override
            public Double getTotalAmount() {
                return totalAmount;
            }
        };
    }
}
