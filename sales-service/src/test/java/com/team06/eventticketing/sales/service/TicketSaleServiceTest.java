package com.team06.eventticketing.sales.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.events.PaymentCompletedEvent;
import com.team06.eventticketing.contracts.events.PaymentInitiatedEvent;
import com.team06.eventticketing.contracts.events.PaymentRefundedEvent;
import com.team06.eventticketing.contracts.feign.BookingServiceClient;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import com.team06.eventticketing.sales.adapter.MongoDocumentAdapter;
import com.team06.eventticketing.sales.adapter.TierRevenueRowAdapter;
import com.team06.eventticketing.sales.dto.ProcessBookingSaleRequest;
import com.team06.eventticketing.sales.dto.RevenueReportDTO;
import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.dto.SaleAuditTrailDTO;
import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.TicketSaleRequest;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.dto.TierRevenueDTO;
import com.team06.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team06.eventticketing.sales.model.Promotion;
import com.team06.eventticketing.sales.model.PromotionDiscountType;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleMethod;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.repository.BookingJdbcRepository;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import com.team06.eventticketing.sales.repository.TierRevenueJdbcRepository;
import com.team06.eventticketing.sales.repository.UserJdbcRepository;
import com.team06.eventticketing.sales.messaging.PaymentEventPublisher;
import com.team06.eventticketing.sales.strategy.RefundStrategySelector;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
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

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private TierRevenueJdbcRepository tierRevenueJdbcRepository;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private BookingServiceClient bookingServiceClient;

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Captor
    private ArgumentCaptor<TicketSale> ticketSaleCaptor;

    private TicketSaleService ticketSaleService;

    @BeforeEach
    void setUp() {
        ticketSaleService = m3Service();
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
    void getSaleAuditTrailReturnsChronologicalEvents() {
        TicketSale sale = sale(7L, 800.0, TicketSaleStatus.COMPLETED);
        when(ticketSaleRepository.findById(7L)).thenReturn(Optional.of(sale));

        Document created = auditEvent("CREATED", LocalDateTime.of(2026, 4, 26, 10, 0), "CREDIT_CARD", 800.0);
        Document completed = auditEvent("COMPLETED", LocalDateTime.of(2026, 4, 26, 10, 1), "CREDIT_CARD", 800.0);
        Document promoted = auditEvent("PROMOTION_APPLIED", LocalDateTime.of(2026, 4, 26, 10, 2), "CREDIT_CARD", 800.0);
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("payment_audit_trail")))
                .thenReturn(List.of(created, completed, promoted));

        SaleAuditTrailDTO trail = ticketSaleService.getSaleAuditTrail(7L);

        assertEquals(7L, trail.getSaleId());
        assertEquals(List.of("CREATED", "COMPLETED", "PROMOTION_APPLIED"),
                trail.getEvents().stream().map(event -> event.getAction()).toList());
        assertEquals("CREDIT_CARD", trail.getEvents().get(0).getMethod());
        assertEquals(800.0, trail.getEvents().get(0).getAmount());
        assertEquals(null, trail.getEvents().get(2).getMethod());
        assertEquals(null, trail.getEvents().get(2).getAmount());
    }

    @Test
    void getSaleAuditTrailReturnsEmptyEventsWhenNoAuditDocumentsExist() {
        TicketSale sale = sale(8L, 100.0, TicketSaleStatus.PENDING);
        when(ticketSaleRepository.findById(8L)).thenReturn(Optional.of(sale));
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("payment_audit_trail")))
                .thenReturn(List.of());

        SaleAuditTrailDTO trail = ticketSaleService.getSaleAuditTrail(8L);

        assertEquals(8L, trail.getSaleId());
        assertEquals(0, trail.getEvents().size());
    }

    @Test
    void getSaleAuditTrailThrows404BeforeQueryingMongoForUnknownSale() {
        when(ticketSaleRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.getSaleAuditTrail(999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(mongoTemplate, never()).find(any(Query.class), eq(Document.class), eq("payment_audit_trail"));
    }

    @Test
    void getTierRevenueMapsPdfScenarioRowsAndCachesForTenMinutes() {
        TicketSaleService service = m2Service();
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        TicketSale first = sale(1L, 3500.0, TicketSaleStatus.COMPLETED);
        TicketSale second = sale(2L, 600.0, TicketSaleStatus.COMPLETED);
        TicketSale third = sale(3L, 100.0, TicketSaleStatus.COMPLETED);
        when(ticketSaleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive))
                .thenReturn(List.of(first, second, third));

        List<TierRevenueDTO> result = service.getTierRevenue(startDate, endDate);

        assertEquals(1, result.size());
        assertEquals("UNSPECIFIED", result.get(0).getTier());
        assertEquals(4200.0, result.get(0).getTotalRevenue());
        assertEquals(3L, result.get(0).getSaleCount());
        assertEquals(3L, result.get(0).getTicketsSold());
        assertEquals(1400.0, result.get(0).getAverageRevenuePerSale());
        verify(redisCacheService).put("sales-service::S5-F10::2026-04-01T00:00::2026-04-30T23:59:59.999999999", result, 600);
    }

    @Test
    void tierRevenueDtoExposesStaticFluentBuilder() throws Exception {
        Method builderMethod = TierRevenueDTO.class.getDeclaredMethod("builder");
        Object builder = builderMethod.invoke(null);

        assertEquals(true, Modifier.isStatic(builderMethod.getModifiers()));
        assertEquals("Builder", builder.getClass().getSimpleName());
        assertEquals(builder, builder.getClass().getDeclaredMethod("tier", String.class).invoke(builder, "VIP"));
        assertEquals(builder, builder.getClass().getDeclaredMethod("totalRevenue", double.class).invoke(builder, 3500.0));
        assertEquals(TierRevenueDTO.class, builder.getClass().getDeclaredMethod("build").getReturnType());
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

        when(bookingServiceClient.getBooking(44L)).thenReturn(new BookingDTO(44L, 91L, 5L, "COMPLETED", 650.0));
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

        when(bookingServiceClient.getBooking(44L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.processBookingSale(44L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void processBookingSaleRejectsNonCompletedBooking() {
        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.CREDIT_CARD);

        when(bookingServiceClient.getBooking(44L)).thenReturn(new BookingDTO(44L, 91L, 5L, "PENDING", 650.0));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.processBookingSale(44L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void processBookingSaleRejectsAlreadyPaidBooking() {
        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.WALLET);

        when(bookingServiceClient.getBooking(44L)).thenReturn(new BookingDTO(44L, 91L, 5L, "COMPLETED", 650.0));
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

        when(bookingServiceClient.getBooking(44L)).thenReturn(new BookingDTO(44L, 91L, 5L, "COMPLETED", 650.0));
        when(ticketSaleRepository.existsByBookingIdAndStatus(44L, TicketSaleStatus.COMPLETED)).thenReturn(false);
        when(ticketSaleRepository.findByBookingIdForUpdate(44L)).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.processBookingSale(44L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void processBookingSaleWithFeignRequiresPaymentPendingAndPublishesCompleted() {
        TicketSaleService service = sagaService();
        TicketSale ticketSale = sale(12L, 650.0, TicketSaleStatus.PENDING);
        ticketSale.setBookingId(44L);

        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.CREDIT_CARD);
        request.setCardLastFour("4242");

        when(bookingServiceClient.getBooking(44L)).thenReturn(new BookingDTO(44L, 91L, 5L, "PAYMENT_PENDING", 650.0));
        when(ticketSaleRepository.existsByBookingIdAndStatus(44L, TicketSaleStatus.COMPLETED)).thenReturn(false);
        when(ticketSaleRepository.findByBookingIdForUpdate(44L)).thenReturn(List.of(ticketSale));
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketSaleResponse response = service.processBookingSale(44L, request);

        assertEquals(TicketSaleStatus.COMPLETED, response.getStatus());
        verify(bookingServiceClient).getBooking(44L);
        verify(paymentEventPublisher).publishPaymentCompleted(any(PaymentCompletedEvent.class));
        verify(bookingJdbcRepository, never()).findByIdForUpdate(44L);
    }

    @Test
    void processBookingSaleWithFeignRejectsNonPaymentPendingBooking() {
        TicketSaleService service = sagaService();
        ProcessBookingSaleRequest request = new ProcessBookingSaleRequest();
        request.setMethod(TicketSaleMethod.CREDIT_CARD);

        when(bookingServiceClient.getBooking(44L)).thenReturn(new BookingDTO(44L, 91L, 5L, "CONFIRMED", 650.0));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.processBookingSale(44L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleRepository, never()).findByBookingIdForUpdate(44L);
    }

    @Test
    void bookingCompletedCreatesPendingSaleAndPublishesPaymentInitiated() {
        TicketSaleService service = sagaService();
        when(ticketSaleRepository.findFirstByBookingIdOrderByIdAsc(10L)).thenReturn(Optional.empty());
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> {
            TicketSale sale = invocation.getArgument(0);
            sale.setId(77L);
            return sale;
        });

        TicketSaleResponse response = service.handleBookingCompleted(
                new BookingCompletedEvent(10L, 20L, 30L, BigDecimal.valueOf(800)));

        TicketSale saved = ticketSaleCaptor.getValue();
        assertEquals(10L, saved.getBookingId());
        assertEquals(20L, saved.getUserId());
        assertEquals(800.0, saved.getAmount());
        assertEquals(TicketSaleStatus.PENDING, saved.getStatus());
        assertEquals(77L, response.getId());
        verify(userServiceClient).getUser(20L);
        verify(paymentEventPublisher).publishPaymentInitiated(any(PaymentInitiatedEvent.class));
    }

    @Test
    void bookingCompletedIsIdempotentForExistingPendingSale() {
        TicketSaleService service = sagaService();
        TicketSale existing = sale(77L, 800.0, TicketSaleStatus.PENDING);
        existing.setBookingId(10L);
        when(ticketSaleRepository.findFirstByBookingIdOrderByIdAsc(10L)).thenReturn(Optional.of(existing));

        service.handleBookingCompleted(new BookingCompletedEvent(10L, 20L, 30L, BigDecimal.valueOf(800)));

        verify(ticketSaleRepository, never()).save(any(TicketSale.class));
        verify(paymentEventPublisher).publishPaymentInitiated(any(PaymentInitiatedEvent.class));
    }

    @Test
    void bookingCancelledRefundsPendingOrCompletedSaleAndPublishesRefunded() {
        TicketSaleService service = sagaService();
        TicketSale existing = sale(77L, 800.0, TicketSaleStatus.COMPLETED);
        existing.setBookingId(10L);
        when(ticketSaleRepository.findFirstByBookingIdOrderByIdAsc(10L)).thenReturn(Optional.of(existing));
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.handleBookingCancelled(new BookingCancelledEvent(10L, 20L, 30L, "payment_failed"));

        assertEquals(TicketSaleStatus.REFUNDED, ticketSaleCaptor.getValue().getStatus());
        assertEquals("payment_failed", ticketSaleCaptor.getValue().getTransactionDetails().get("refundReason"));
        verify(paymentEventPublisher).publishPaymentRefunded(any(PaymentRefundedEvent.class));
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
    void processRefundWithWindowPolicyAppliesFullRefundAndWritesStrategyDetails() {
        TicketSaleService service = m2Service();
        TicketSale sale = sale(91L, 300.0, TicketSaleStatus.COMPLETED);
        sale.setTransactionDetails(new LinkedHashMap<>(Map.of("gatewayResponse", "approved")));
        RefundRequest request = refundRequest("unable_to_attend");
        List<String> actions = new ArrayList<>();
        List<Object> payloads = new ArrayList<>();
        service.register((action, payload) -> {
            actions.add(action);
            payloads.add(payload);
        });

        when(ticketSaleRepository.findById(91L)).thenReturn(Optional.of(sale));
        when(bookingServiceClient.getBooking(sale.getBookingId())).thenReturn(bookingForSale(sale, 15L));
        when(eventServiceClient.getEvent(15L)).thenReturn(event(15L, LocalDateTime.now().plusDays(7)));
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketSaleResponse response = service.processRefundWithWindowPolicy(91L, request);

        TicketSale saved = ticketSaleCaptor.getValue();
        assertEquals(TicketSaleStatus.REFUNDED, response.getStatus());
        assertEquals(TicketSaleStatus.REFUNDED, saved.getStatus());
        assertEquals(300.0, saved.getTransactionDetails().get("refundAmount"));
        assertEquals("FullWindowRefundStrategy", saved.getTransactionDetails().get("refundPolicy"));
        assertEquals("unable_to_attend", saved.getTransactionDetails().get("refundReason"));
        assertEquals(List.of("REFUNDED"), actions);
        assertEquals(true, payloads.get(0).toString().contains("FullWindowRefundStrategy"));
        verifyRefundWindowCacheInvalidation(91L);
    }

    @Test
    void processRefundWithWindowPolicyAppliesPartialRefundForThirtySixHourWindow() {
        TicketSaleService service = m2Service();
        TicketSale sale = sale(92L, 400.0, TicketSaleStatus.COMPLETED);
        RefundRequest request = refundRequest("unable_to_attend");

        when(ticketSaleRepository.findById(92L)).thenReturn(Optional.of(sale));
        when(bookingServiceClient.getBooking(sale.getBookingId())).thenReturn(bookingForSale(sale, 16L));
        when(eventServiceClient.getEvent(16L)).thenReturn(event(16L, LocalDateTime.now().plusHours(36)));
        when(ticketSaleRepository.save(ticketSaleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        TicketSaleResponse response = service.processRefundWithWindowPolicy(92L, request);

        assertEquals(TicketSaleStatus.REFUNDED, response.getStatus());
        assertEquals(200.0, ticketSaleCaptor.getValue().getTransactionDetails().get("refundAmount"));
        assertEquals("PartialWindowRefundStrategy", ticketSaleCaptor.getValue().getTransactionDetails().get("refundPolicy"));
    }

    @Test
    void processRefundWithWindowPolicyLogsDeniedRefundAndInvalidatesCachesBeforeThrowing() {
        TicketSaleService service = m2Service();
        TicketSale sale = sale(93L, 250.0, TicketSaleStatus.COMPLETED);
        RefundRequest request = refundRequest("unable_to_attend");
        List<String> actions = new ArrayList<>();
        List<Object> payloads = new ArrayList<>();
        service.register((action, payload) -> {
            actions.add(action);
            payloads.add(payload);
        });

        when(ticketSaleRepository.findById(93L)).thenReturn(Optional.of(sale));
        when(bookingServiceClient.getBooking(sale.getBookingId())).thenReturn(bookingForSale(sale, 17L));
        when(eventServiceClient.getEvent(17L)).thenReturn(event(17L, LocalDateTime.now().plusHours(6)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.processRefundWithWindowPolicy(93L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("refund window expired", exception.getReason());
        assertEquals(List.of("REFUND_DENIED"), actions);
        assertEquals(true, payloads.get(0).toString().contains("NoRefundStrategy"));
        verify(ticketSaleRepository, never()).save(any(TicketSale.class));
        verifyRefundWindowCacheInvalidation(93L);
    }

    @Test
    void processRefundWithWindowPolicyRejectsPendingRefundedMissingAndNoEventCases() {
        TicketSale pending = sale(94L, 250.0, TicketSaleStatus.PENDING);
        TicketSale refunded = sale(95L, 250.0, TicketSaleStatus.REFUNDED);
        TicketSale noEvent = sale(96L, 250.0, TicketSaleStatus.COMPLETED);
        TicketSaleService service = m2Service();

        when(ticketSaleRepository.findById(94L)).thenReturn(Optional.of(pending));
        when(ticketSaleRepository.findById(95L)).thenReturn(Optional.of(refunded));
        when(ticketSaleRepository.findById(96L)).thenReturn(Optional.of(noEvent));
        when(ticketSaleRepository.findById(999L)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(noEvent.getBookingId())).thenReturn(bookingForSale(noEvent, null));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.processRefundWithWindowPolicy(94L, refundRequest("pending"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.processRefundWithWindowPolicy(95L, refundRequest("refunded"))).getStatusCode());
        ResponseStatusException noEventException = assertThrows(ResponseStatusException.class,
                () -> service.processRefundWithWindowPolicy(96L, refundRequest("no_event")));
        assertEquals(HttpStatus.BAD_REQUEST, noEventException.getStatusCode());
        assertEquals("booking has no associated event", noEventException.getReason());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.processRefundWithWindowPolicy(999L, refundRequest("missing"))).getStatusCode());
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
        when(userServiceClient.getUser(55L)).thenReturn(new UserDTO(55L, "User", "user@example.com", "ATTENDEE"));
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
        when(userServiceClient.getUser(77L)).thenReturn(new UserDTO(77L, "User", "user@example.com", "ATTENDEE"));
        when(ticketSaleRepository.getCompletedSalesSummaryByMethod(77L)).thenReturn(List.of());

        UserSaleSummaryDTO summary = ticketSaleService.getUserSaleSummary(77L);

        assertEquals(77L, summary.getUserId());
        assertEquals(0L, summary.getTotalSales());
        assertEquals(0.0, summary.getTotalAmount());
        assertEquals(Map.of(), summary.getMethodBreakdown());
    }

    @Test
    void getUserSaleSummaryRejectsUnknownUser() {
        when(userServiceClient.getUser(999L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.getUserSaleSummary(999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(ticketSaleRepository, never()).getCompletedSalesSummaryByMethod(999L);
    }

    @Test
    void searchTicketSalesReturnsMostRecentMatchesWhenStatusIsProvided() {
        TicketSale newest = sale(21L, 900.0, TicketSaleStatus.COMPLETED);
        newest.setCreatedAt(LocalDateTime.of(2026, 3, 20, 12, 0));
        TicketSale older = sale(22L, 400.0, TicketSaleStatus.COMPLETED);
        older.setCreatedAt(LocalDateTime.of(2026, 3, 5, 9, 0));

        when(ticketSaleRepository.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                TicketSaleStatus.COMPLETED,
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 1).atStartOfDay()
        )).thenReturn(List.of(newest, older));

        List<TicketSaleResponse> result = ticketSaleService.searchTicketSales(
                TicketSaleStatus.COMPLETED,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(2, result.size());
        assertEquals(21L, result.get(0).getId());
        assertEquals(22L, result.get(1).getId());
        verify(ticketSaleRepository).findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                TicketSaleStatus.COMPLETED,
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 1).atStartOfDay()
        );
    }

    @Test
    void searchTicketSalesReturnsAllStatusesWhenFilterIsOmitted() {
        TicketSale refunded = sale(31L, 300.0, TicketSaleStatus.REFUNDED);
        refunded.setCreatedAt(LocalDateTime.of(2026, 3, 18, 16, 0));
        TicketSale pending = sale(32L, 150.0, TicketSaleStatus.PENDING);
        pending.setCreatedAt(LocalDateTime.of(2026, 3, 2, 8, 30));

        when(ticketSaleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 1).atStartOfDay()
        )).thenReturn(List.of(refunded, pending));

        List<TicketSaleResponse> result = ticketSaleService.searchTicketSales(
                null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(2, result.size());
        assertEquals(31L, result.get(0).getId());
        assertEquals(TicketSaleStatus.REFUNDED, result.get(0).getStatus());
        assertEquals(32L, result.get(1).getId());
        verify(ticketSaleRepository).findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                LocalDate.of(2026, 3, 1).atStartOfDay(),
                LocalDate.of(2026, 4, 1).atStartOfDay()
        );
    }

    @Test
    void searchTicketSalesRejectsInvalidDateRange() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> ticketSaleService.searchTicketSales(
                        TicketSaleStatus.COMPLETED,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 3, 31)
                ));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleRepository, never()).findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
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

    private TicketSaleService m2Service() {
        return m3Service();
    }

    private TicketSaleService m3Service() {
        return new TicketSaleService(
                ticketSaleRepository,
                bookingJdbcRepository,
                userJdbcRepository,
                mongoTemplate,
                null,
                new MongoDocumentAdapter(),
                tierRevenueJdbcRepository,
                new TierRevenueRowAdapter(),
                redisCacheService,
                new ObjectMapper(),
                new RefundStrategySelector(),
                bookingServiceClient,
                eventServiceClient,
                userServiceClient,
                paymentEventPublisher);
    }

    private TicketSaleService sagaService() {
        return m3Service();
    }

    private RefundRequest refundRequest(String reason) {
        RefundRequest request = new RefundRequest();
        request.setReason(reason);
        return request;
    }

    private BookingDTO bookingForSale(TicketSale sale, Long eventId) {
        return new BookingDTO(sale.getBookingId(), sale.getUserId(), eventId, "COMPLETED", sale.getAmount());
    }

    private EventDTO event(Long eventId, LocalDateTime eventDate) {
        return new EventDTO(eventId, "Concert", "Cairo Arena", eventDate, "CONCERT", "UPCOMING", 0.0, Map.of());
    }

    private void verifyRefundWindowCacheInvalidation(Long saleId) {
        verify(redisCacheService).deleteByPattern("sales-service::S5-F10::*");
        verify(redisCacheService).delete("sales-service::S5-F11::" + saleId);
        verify(redisCacheService).delete("sales-service::ticket-sale::" + saleId);
    }

    private Document auditEvent(String action, LocalDateTime timestamp, String method, Double amount) {
        return new Document("saleId", 7L)
                .append("action", action)
                .append("timestamp", timestamp)
                .append("method", method)
                .append("amount", amount)
                .append("details", new LinkedHashMap<>(Map.of("source", "test")));
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
