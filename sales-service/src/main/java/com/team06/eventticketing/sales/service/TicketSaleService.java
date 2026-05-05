package com.team06.eventticketing.sales.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.sales.adapter.MongoDocumentAdapter;
import com.team06.eventticketing.sales.adapter.TierRevenueRowAdapter;
import com.team06.eventticketing.sales.dto.ProcessBookingSaleRequest;
import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.dto.RevenueReportDTO;
import com.team06.eventticketing.sales.dto.SaleAuditTrailDTO;
import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.TicketSaleRequest;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.dto.TierRevenueDTO;
import com.team06.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.repository.BookingJdbcRepository;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import com.team06.eventticketing.sales.repository.TierRevenueJdbcRepository;
import com.team06.eventticketing.sales.repository.UserJdbcRepository;
import com.team06.eventticketing.sales.strategy.RefundResult;
import com.team06.eventticketing.sales.strategy.RefundStrategy;
import com.team06.eventticketing.sales.strategy.RefundStrategySelector;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;




@Service
public class TicketSaleService {

    private static final Set<String> SALE_AUDIT_ACTIONS = Set.of(
            "CREATED",
            "COMPLETED",
            "FAILED",
            "REFUNDED",
            "REFUND_DENIED",
            "PROMOTION_APPLIED",
            "RETRY_ATTEMPTED",
            "TRANSFERRED",
            "RESOLD"
    );

    private final TicketSaleRepository ticketSaleRepository;
    private final BookingJdbcRepository bookingJdbcRepository;
    private final UserJdbcRepository userJdbcRepository;
    private final MongoTemplate mongoTemplate;
    private final MongoDocumentAdapter mongoDocumentAdapter;
    private final TierRevenueJdbcRepository tierRevenueJdbcRepository;
    private final TierRevenueRowAdapter tierRevenueRowAdapter;
    private final RedisCacheService redisCacheService;
    private final RefundStrategySelector refundStrategySelector;
    private final JavaType tierRevenueListType;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public TicketSaleService(
            TicketSaleRepository ticketSaleRepository,
            BookingJdbcRepository bookingJdbcRepository,
            UserJdbcRepository userJdbcRepository,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory,
            MongoDocumentAdapter mongoDocumentAdapter,
            TierRevenueJdbcRepository tierRevenueJdbcRepository,
            TierRevenueRowAdapter tierRevenueRowAdapter,
            RedisCacheService redisCacheService,
            ObjectMapper objectMapper,
            RefundStrategySelector refundStrategySelector
    ) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.bookingJdbcRepository = bookingJdbcRepository;
        this.userJdbcRepository = userJdbcRepository;
        this.mongoTemplate = mongoTemplate;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
        this.tierRevenueJdbcRepository = tierRevenueJdbcRepository;
        this.tierRevenueRowAdapter = tierRevenueRowAdapter;
        this.redisCacheService = redisCacheService;
        this.refundStrategySelector = refundStrategySelector;
        this.tierRevenueListType = objectMapper == null
                ? null
                : objectMapper.getTypeFactory().constructCollectionType(List.class, TierRevenueDTO.class);
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public TicketSaleService(
            TicketSaleRepository ticketSaleRepository,
            BookingJdbcRepository bookingJdbcRepository,
            UserJdbcRepository userJdbcRepository,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this(
                ticketSaleRepository,
                bookingJdbcRepository,
                userJdbcRepository,
                mongoTemplate,
                eventFactory,
                new MongoDocumentAdapter(),
                null,
                new TierRevenueRowAdapter(),
                null,
                null,
                new RefundStrategySelector()
        );
    }

    public TicketSaleService(
            TicketSaleRepository ticketSaleRepository,
            BookingJdbcRepository bookingJdbcRepository,
            UserJdbcRepository userJdbcRepository
    ) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.bookingJdbcRepository = bookingJdbcRepository;
        this.userJdbcRepository = userJdbcRepository;
        this.mongoTemplate = null;
        this.mongoDocumentAdapter = new MongoDocumentAdapter();
        this.tierRevenueJdbcRepository = null;
        this.tierRevenueRowAdapter = new TierRevenueRowAdapter();
        this.redisCacheService = null;
        this.refundStrategySelector = new RefundStrategySelector();
        this.tierRevenueListType = null;
    }

    @Transactional(readOnly = true)
    public List<TicketSaleResponse> getAllTicketSales() {
        return ticketSaleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TicketSaleResponse getTicketSaleById(Long id) {
        return toResponse(findTicketSale(id));
    }

    @Transactional(readOnly = true)
    public SaleAuditTrailDTO getSaleAuditTrail(Long id) {
        findTicketSale(id);

        List<Document> events = mongoTemplate == null
                ? List.of()
                : mongoTemplate.find(
                        Query.query(Criteria.where("saleId").is(id).and("action").in(SALE_AUDIT_ACTIONS))
                                .with(Sort.by(Sort.Direction.ASC, "timestamp")),
                        Document.class,
                        "payment_audit_trail"
                );

        SaleAuditTrailDTO trail = new SaleAuditTrailDTO();
        trail.setSaleId(id);
        trail.setEvents(events.stream().map(mongoDocumentAdapter::adapt).toList());
        return trail;
    }

    @Transactional(readOnly = true)
    public List<TierRevenueDTO> getTierRevenue(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        notifyObservers("ANALYTICS_VIEWED", Map.of(
                "details", Map.of(
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString())));

        LocalDateTime startInclusive = startDate.atStartOfDay();
        LocalDateTime endInclusive = endDate.atTime(LocalTime.MAX);
        String cacheKey = "sales-service::S5-F10::" + startInclusive + "::" + endInclusive;
        if (redisCacheService != null && tierRevenueListType != null) {
            List<TierRevenueDTO> cached = redisCacheService.get(cacheKey, tierRevenueListType);
            if (cached != null) {
                return cached;
            }
        }

        List<TierRevenueDTO> response = tierRevenueJdbcRepository == null
                ? List.of()
                : tierRevenueJdbcRepository.aggregateByTier(startInclusive, endInclusive).stream()
                        .map(tierRevenueRowAdapter::adapt)
                        .toList();
        if (redisCacheService != null) {
            redisCacheService.put(cacheKey, response, 600);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public SaleDetailsDTO getTicketSaleDetails(Long id) {
        TicketSale ticketSale = ticketSaleRepository.findByIdWithSalePromotions(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));

        List<SaleDetailsDTO.AppliedPromotionDTO> appliedPromotions = ticketSale.getSalePromotions().stream()
                .sorted((first, second) -> first.getAppliedAt().compareTo(second.getAppliedAt()))
                .map(this::toAppliedPromotion)
                .toList();

        double totalDiscount = appliedPromotions.stream()
                .mapToDouble(promotion -> promotion.getDiscountApplied() == null ? 0.0 : promotion.getDiscountApplied())
                .sum();
        double originalAmount = ticketSale.getAmount() == null ? 0.0 : ticketSale.getAmount();

        return SaleDetailsDTO.builder()
                .saleId(ticketSale.getId())
                .bookingId(ticketSale.getBookingId())
                .userId(ticketSale.getUserId())
                .originalAmount(originalAmount)
                .method(ticketSale.getMethod())
                .status(ticketSale.getStatus())
                .transactionDetails(copyTransactionDetails(ticketSale.getTransactionDetails()))
                .appliedPromotions(appliedPromotions)
                .totalDiscount(totalDiscount)
                .finalAmount(originalAmount - totalDiscount)
                .build();
    }

    @Transactional
    public TicketSaleResponse createTicketSale(TicketSaleRequest request) {
        TicketSale ticketSale = new TicketSale();
        apply(ticketSale, request);
        TicketSale saved = ticketSaleRepository.save(ticketSale);
        notifyObservers("SALE_CREATED", buildAuditPayload(saved, null));
        return toResponse(saved);
    }

    @Transactional
    public TicketSaleResponse updateTicketSale(Long id, TicketSaleRequest request) {
        TicketSale existing = findTicketSale(id);
        apply(existing, request);
        TicketSale saved = ticketSaleRepository.save(existing);
        notifyObservers("SALE_UPDATED", buildAuditPayload(saved, null));
        return toResponse(saved);
    }

    @Transactional
    public void deleteTicketSale(Long id) {
        TicketSale ticketSale = findTicketSale(id);
        ticketSaleRepository.delete(ticketSale);
        notifyObservers("SALE_DELETED", buildAuditPayload(ticketSale, null));
    }

    @Transactional
    public TicketSaleResponse processBookingSale(Long bookingId, ProcessBookingSaleRequest request) {
        return processBookingSale(bookingId, request, false);
    }

    @Transactional
    public TicketSaleResponse processBookingSale(Long bookingId, ProcessBookingSaleRequest request, boolean simulateFailure) {
        if (request == null || request.getMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is required");
        }

        BookingJdbcRepository.BookingPaymentRow booking = bookingJdbcRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!"COMPLETED".equals(booking.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking must be completed before payment");
        }

        if (ticketSaleRepository.existsByBookingIdAndStatus(bookingId, TicketSaleStatus.COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
        }

        TicketSale ticketSale = findPendingTicketSaleForBooking(bookingId);
        ticketSale.setMethod(request.getMethod());
        if (booking.totalAmount() != null) {
            ticketSale.setAmount(booking.totalAmount());
        }
        Map<String, Object> transactionDetails = buildProcessedTransactionDetails(ticketSale, request);
        ticketSale.setTransactionDetails(transactionDetails);
        notifyObservers("CREATED", buildAuditPayload(ticketSale, null));

        TicketSale saved;
        if (simulateFailure) {
            transactionDetails.put("gatewayResponse", "declined");
            transactionDetails.put("failedAt", LocalDateTime.now().toString());
            transactionDetails.put("failureReason", "Simulated gateway failure");
            ticketSale.setStatus(TicketSaleStatus.FAILED);
            saved = ticketSaleRepository.save(ticketSale);
            notifyObservers("FAILED", buildAuditPayload(saved, Map.of("simulateFailure", true)));
        } else {
            transactionDetails.put("gatewayResponse", "approved");
            transactionDetails.put("paidAt", LocalDateTime.now().toString());
            ticketSale.setStatus(TicketSaleStatus.COMPLETED);
            saved = ticketSaleRepository.save(ticketSale);
            notifyObservers("COMPLETED", buildAuditPayload(saved, null));
        }

        return toResponse(saved);
    }

    @Transactional
    public TicketSale retryFailedSale(Long id) {
        TicketSale sale = findTicketSale(id);
        if (sale.getStatus() != TicketSaleStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale is not in FAILED status");
        }

        Map<String, Object> details = new LinkedHashMap<>(copyTransactionDetails(sale.getTransactionDetails()));
        int retryAttempt = 0;
        Object rawRetry = details.get("retryAttempt");
        if (rawRetry instanceof Number) {
            retryAttempt = ((Number) rawRetry).intValue();
        }

        details.put("retryAttempt", retryAttempt + 1);
        details.put("gatewayResponse", "approved");
        sale.setTransactionDetails(details);
        sale.setStatus(TicketSaleStatus.COMPLETED);

        TicketSale saved = ticketSaleRepository.save(sale);
        notifyObservers("RETRY_ATTEMPTED", buildAuditPayload(saved, Map.of("retryAttempt", retryAttempt + 1)));
        notifyObservers("COMPLETED", buildAuditPayload(saved, Map.of("retryAttempt", retryAttempt + 1)));
        return saved;
    }

    @Transactional
    public TicketSale refundTicketSale(Long id, RefundRequest request) {
        TicketSale sale = findTicketSale(id);
        if (sale.getStatus() != TicketSaleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale must be completed before refund");
        }

        Map<String, Object> details = new LinkedHashMap<>(copyTransactionDetails(sale.getTransactionDetails()));
        details.put("refundReason", request == null ? null : request.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());

        sale.setTransactionDetails(details);
        sale.setStatus(TicketSaleStatus.REFUNDED);
        TicketSale saved = ticketSaleRepository.save(sale);
        Map<String, Object> extraDetails = new LinkedHashMap<>();
        extraDetails.put("refundReason", request == null ? null : request.getReason());
        notifyObservers("REFUNDED", buildAuditPayload(saved, extraDetails));
        return saved;
    }

    private TicketSale findTicketSale(Long id) {
        return ticketSaleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));
    }

    private void apply(TicketSale ticketSale, TicketSaleRequest request) {
        ticketSale.setBookingId(request.getBookingId());
        ticketSale.setUserId(request.getUserId());
        ticketSale.setAmount(request.getAmount());
        ticketSale.setMethod(request.getMethod());
        ticketSale.setStatus(request.getStatus());
        ticketSale.setTransactionDetails(copyTransactionDetails(request.getTransactionDetails()));
    }

    private TicketSale findPendingTicketSaleForBooking(Long bookingId) {
        List<TicketSale> ticketSales = ticketSaleRepository.findByBookingIdForUpdate(bookingId);
        if (ticketSales.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found for booking");
        }
        if (ticketSales.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, ticketSales.size());
        }

        TicketSale ticketSale = ticketSales.get(0);
        if (ticketSale.getStatus() != TicketSaleStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale must be pending for payment");
        }
        return ticketSale;
    }

    private TicketSaleResponse toResponse(TicketSale ticketSale) {
        return TicketSaleResponse.builder()
                .id(ticketSale.getId())
                .bookingId(ticketSale.getBookingId())
                .userId(ticketSale.getUserId())
                .amount(ticketSale.getAmount())
                .method(ticketSale.getMethod())
                .status(ticketSale.getStatus())
                .transactionDetails(copyTransactionDetails(ticketSale.getTransactionDetails()))
                .createdAt(ticketSale.getCreatedAt())
                .build();
    }

    private SaleDetailsDTO.AppliedPromotionDTO toAppliedPromotion(SalePromotion salePromotion) {
        return SaleDetailsDTO.AppliedPromotionDTO.builder()
                .promotionCode(salePromotion.getPromotion().getCode())
                .discountType(salePromotion.getPromotion().getDiscountType())
                .discountApplied(salePromotion.getDiscountApplied())
                .appliedAt(salePromotion.getAppliedAt())
                .build();
    }

    private Map<String, Object> copyTransactionDetails(Map<String, Object> transactionDetails) {
        return transactionDetails == null ? new LinkedHashMap<>() : new LinkedHashMap<>(transactionDetails);
    }

    private Map<String, Object> buildProcessedTransactionDetails(
            TicketSale ticketSale,
            ProcessBookingSaleRequest request
    ) {
        Map<String, Object> transactionDetails = new LinkedHashMap<>(copyTransactionDetails(ticketSale.getTransactionDetails()));
        transactionDetails.put("paymentMethod", request.getMethod().name());
        transactionDetails.put("attemptedAt", LocalDateTime.now().toString());

        if (request.getCardLastFour() != null && !request.getCardLastFour().isBlank()) {
            transactionDetails.put("cardLastFour", request.getCardLastFour().trim());
        }

        return transactionDetails;
    }

    @Transactional(readOnly = true)
    public RevenueReportDTO getRevenueReport(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<TicketSale> sales = ticketSaleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                startDateTime,
                endDateTime
        );

        double totalRevenue = sales.stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.COMPLETED)
                .mapToDouble(sale -> sale.getAmount() == null ? 0.0 : sale.getAmount())
                .sum();

        long totalTransactions = sales.stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.COMPLETED)
                .count();

        double averageSale = totalTransactions == 0 ? 0.0 : totalRevenue / totalTransactions;

        double refundedAmount = sales.stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.REFUNDED)
                .mapToDouble(sale -> sale.getAmount() == null ? 0.0 : sale.getAmount())
                .sum();

        long refundCount = sales.stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.REFUNDED)
                .count();

        return RevenueReportDTO.builder()
                .totalRevenue(totalRevenue)
                .totalTransactions(totalTransactions)
                .averageSale(averageSale)
                .refundedAmount(refundedAmount)
                .refundCount(refundCount)
                .build();
    }

    @Transactional(readOnly = true)
    public UserSaleSummaryDTO getUserSaleSummary(Long userId) {
        if (!userJdbcRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        List<TicketSaleRepository.PaymentMethodSummaryProjection> rows =
                ticketSaleRepository.getCompletedSalesSummaryByMethod(userId);

        Map<String, Double> methodBreakdown = new LinkedHashMap<>();
        Map<String, Long> methodCounts = new LinkedHashMap<>();
        long totalSales = 0L;
        double totalAmount = 0.0;

        for (TicketSaleRepository.PaymentMethodSummaryProjection row : rows) {
            String method = row.getMethod();
            Long saleCount = row.getSaleCount() == null ? 0L : row.getSaleCount();
            Double amount = row.getTotalAmount() == null ? 0.0 : row.getTotalAmount();

            methodBreakdown.put(method, amount);
            methodCounts.put(method, saleCount);
            totalSales += saleCount;
            totalAmount += amount;
        }

        return UserSaleSummaryDTO.builder()
                .userId(userId)
                .totalSales(totalSales)
                .totalAmount(totalAmount)
                .methodBreakdown(new LinkedHashMap<>(methodBreakdown))
                .methodCounts(new LinkedHashMap<>(methodCounts))
                .build();
    }

    @Transactional(readOnly = true)
    public List<TicketSaleResponse> searchTicketSales(
            TicketSaleStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be on or before endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<TicketSale> sales;
        if (status != null) {
            sales = ticketSaleRepository.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                    status,
                    startDateTime,
                    endDateTime
            );
        } else {
            sales = ticketSaleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                    startDateTime,
                    endDateTime
            );
        }

        return sales.stream().map(this::toResponse).toList();
    }

    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String action, Object payload) {
        observers.forEach(observer -> observer.onEvent(action, payload));
    }

    private Map<String, Object> buildAuditPayload(TicketSale ticketSale, @Nullable Map<String, Object> extraDetails) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bookingId", ticketSale.getBookingId());
        details.put("userId", ticketSale.getUserId());
        details.put("status", ticketSale.getStatus() == null ? null : ticketSale.getStatus().name());
        details.put("transactionDetails", copyTransactionDetails(ticketSale.getTransactionDetails()));
        if (extraDetails != null) {
            details.putAll(extraDetails);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("saleId", ticketSale.getId());
        payload.put("details", details);
        if (ticketSale.getMethod() != null) {
            payload.put("method", ticketSale.getMethod().name());
        }
        if (ticketSale.getAmount() != null) {
            payload.put("amount", ticketSale.getAmount());
        }
        return payload;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.PAYMENT_AUDIT, "payment_audit_trail"));
        }
    }


    @Transactional
    public TicketSaleResponse processRefundWithWindowPolicy(Long id, RefundRequest request) {
        TicketSale sale = findTicketSale(id);

        if (sale.getStatus() != TicketSaleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale must be completed before refund");
        }

        BookingJdbcRepository.SaleEventDateRow eventRow = bookingJdbcRepository.findEventDateByTicketSaleId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking has no associated event"));

        if (eventRow.eventId() == null || eventRow.eventDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking has no associated event");
        }

        LocalDateTime eventDate = eventRow.eventDate();
        RefundStrategy strategy = refundStrategySelector.select(sale, eventDate);
        RefundResult result = strategy.calculateRefund(sale, request, eventDate);

        if ("refund window expired".equals(result.getReasonCode())) {
            Map<String, Object> deniedDetails = new LinkedHashMap<>();
            deniedDetails.put("refundPolicy", result.getStrategyName());
            deniedDetails.put("denialReason", result.getReasonCode());
            deniedDetails.put("eventLeadTimeHours", result.getEventLeadTimeHours());
            deniedDetails.put("refundReason", request == null ? null : request.getReason());

            notifyObservers("REFUND_DENIED", buildAuditPayload(sale, deniedDetails));
            invalidateRefundWindowCaches(id);

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refund window expired");
        }

        Map<String, Object> details = new LinkedHashMap<>(copyTransactionDetails(sale.getTransactionDetails()));
        details.put("refundAmount", result.getAmount());
        details.put("refundPolicy", result.getStrategyName());
        details.put("refundReason", request == null ? null : request.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());

        sale.setTransactionDetails(details);
        sale.setStatus(TicketSaleStatus.REFUNDED);

        TicketSale saved = ticketSaleRepository.save(sale);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("refundPolicy", result.getStrategyName());
        auditDetails.put("refundReason", request == null ? null : request.getReason());
        auditDetails.put("originalAmount", sale.getAmount());
        auditDetails.put("refundAmount", result.getAmount());
        auditDetails.put("eventLeadTimeHours", result.getEventLeadTimeHours());

        notifyObservers("REFUNDED", buildAuditPayload(saved, auditDetails));
        invalidateRefundWindowCaches(id);

        return toResponse(saved);
    }


    private void invalidateRefundWindowCaches(Long saleId) {
        if (redisCacheService == null) {
            return;
        }

        redisCacheService.deleteByPattern("sales-service::S5-F10::*");
        redisCacheService.deleteByPattern("sales-service::S5-F11::*");
        redisCacheService.delete("sales-service::ticket-sale::" + saleId);
    }




}
