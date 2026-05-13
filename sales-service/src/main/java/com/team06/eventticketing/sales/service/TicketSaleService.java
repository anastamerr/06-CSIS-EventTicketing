package com.team06.eventticketing.sales.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.events.PaymentCompletedEvent;
import com.team06.eventticketing.contracts.events.PaymentFailedEvent;
import com.team06.eventticketing.contracts.events.PaymentInitiatedEvent;
import com.team06.eventticketing.contracts.events.PaymentRefundedEvent;
import com.team06.eventticketing.contracts.feign.BookingServiceClient;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import com.team06.eventticketing.sales.messaging.PaymentEventPublisher;
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
import com.team06.eventticketing.sales.model.TicketSaleMethod;
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
import java.math.BigDecimal;
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
import feign.FeignException;




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
    private final BookingServiceClient bookingServiceClient;
    private final EventServiceClient eventServiceClient;
    private final UserServiceClient userServiceClient;
    private final PaymentEventPublisher paymentEventPublisher;
    private final JavaType tierRevenueListType;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

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
        this(
                ticketSaleRepository,
                bookingJdbcRepository,
                userJdbcRepository,
                mongoTemplate,
                eventFactory,
                mongoDocumentAdapter,
                tierRevenueJdbcRepository,
                tierRevenueRowAdapter,
                redisCacheService,
                objectMapper,
                refundStrategySelector,
                null,
                null,
                null,
                null
        );
    }

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
            RefundStrategySelector refundStrategySelector,
            @Nullable BookingServiceClient bookingServiceClient,
            @Nullable UserServiceClient userServiceClient,
            @Nullable PaymentEventPublisher paymentEventPublisher
    ) {
        this(
                ticketSaleRepository,
                bookingJdbcRepository,
                userJdbcRepository,
                mongoTemplate,
                eventFactory,
                mongoDocumentAdapter,
                tierRevenueJdbcRepository,
                tierRevenueRowAdapter,
                redisCacheService,
                objectMapper,
                refundStrategySelector,
                bookingServiceClient,
                null,
                userServiceClient,
                paymentEventPublisher
        );
    }

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
            RefundStrategySelector refundStrategySelector,
            @Nullable BookingServiceClient bookingServiceClient,
            @Nullable EventServiceClient eventServiceClient,
            @Nullable UserServiceClient userServiceClient,
            @Nullable PaymentEventPublisher paymentEventPublisher
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
        this.bookingServiceClient = bookingServiceClient;
        this.eventServiceClient = eventServiceClient;
        this.userServiceClient = userServiceClient;
        this.paymentEventPublisher = paymentEventPublisher;
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
                new RefundStrategySelector(),
                null,
                null,
                null,
                null
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
        this.bookingServiceClient = null;
        this.eventServiceClient = null;
        this.userServiceClient = null;
        this.paymentEventPublisher = null;
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
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        String cacheKey = "sales-service::S5-F10::" + startInclusive + "::" + endInclusive;
        if (redisCacheService != null && tierRevenueListType != null) {
            List<TierRevenueDTO> cached = redisCacheService.get(cacheKey, tierRevenueListType);
            if (cached != null) {
                return cached;
            }
        }

        List<TicketSale> completedSales = ticketSaleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                startInclusive,
                endExclusive
        ).stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.COMPLETED)
                .toList();
        double totalRevenue = completedSales.stream()
                .mapToDouble(sale -> sale.getAmount() == null ? 0.0 : sale.getAmount())
                .sum();
        long saleCount = completedSales.size();
        List<TierRevenueDTO> response = saleCount == 0
                ? List.of()
                : List.of(TierRevenueDTO.builder()
                        .tier("UNSPECIFIED")
                        .totalRevenue(totalRevenue)
                        .saleCount(saleCount)
                        .ticketsSold(saleCount)
                        .averageRevenuePerSale(totalRevenue / saleCount)
                        .build());
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

        BookingDTO booking = fetchBooking(bookingId);
        if (!"PAYMENT_PENDING".equals(booking.status()) && !"COMPLETED".equals(booking.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking is not awaiting payment. Status: " + booking.status());
        }
        Double bookingTotalAmount = booking.totalAmount();

        if (ticketSaleRepository.existsByBookingIdAndStatus(bookingId, TicketSaleStatus.COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
        }

        TicketSale ticketSale = findPendingTicketSaleForBooking(bookingId);
        ticketSale.setMethod(request.getMethod());
        if (bookingTotalAmount != null) {
            ticketSale.setAmount(bookingTotalAmount);
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
            publishPaymentFailed(saved, "Simulated gateway failure");
        } else {
            transactionDetails.put("gatewayResponse", "approved");
            transactionDetails.put("paidAt", LocalDateTime.now().toString());
            ticketSale.setStatus(TicketSaleStatus.COMPLETED);
            saved = ticketSaleRepository.save(ticketSale);
            notifyObservers("COMPLETED", buildAuditPayload(saved, null));
            publishPaymentCompleted(saved);
        }

        return toResponse(saved);
    }

    @Transactional
    public TicketSaleResponse handleBookingCompleted(BookingCompletedEvent event) {
        if (event == null || event.bookingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking.completed event is missing bookingId");
        }

        if (userServiceClient != null && event.userId() != null) {
            userServiceClient.getUser(event.userId());
        }

        TicketSale sale = ticketSaleRepository.findFirstByBookingIdOrderByIdAsc(event.bookingId())
                .orElseGet(() -> {
                    TicketSale pending = new TicketSale();
                    pending.setBookingId(event.bookingId());
                    pending.setUserId(event.userId());
                    pending.setAmount(toDouble(event.totalAmount()));
                    pending.setMethod(TicketSaleMethod.CREDIT_CARD);
                    pending.setStatus(TicketSaleStatus.PENDING);
                    pending.setTransactionDetails(new LinkedHashMap<>(Map.of(
                            "sagaState", "PAYMENT_INITIATED",
                            "initiatedAt", LocalDateTime.now().toString())));
                    return ticketSaleRepository.save(pending);
                });

        if (sale.getStatus() == TicketSaleStatus.PENDING) {
            publishPaymentInitiated(sale);
        }
        return toResponse(sale);
    }

    @Transactional
    public void handleBookingCancelled(BookingCancelledEvent event) {
        if (event == null || event.bookingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking.cancelled event is missing bookingId");
        }

        ticketSaleRepository.findFirstByBookingIdOrderByIdAsc(event.bookingId())
                .filter(sale -> sale.getStatus() == TicketSaleStatus.PENDING || sale.getStatus() == TicketSaleStatus.COMPLETED)
                .ifPresent(sale -> {
                    Map<String, Object> details = new LinkedHashMap<>(copyTransactionDetails(sale.getTransactionDetails()));
                    details.put("refundReason", event.reason());
                    details.put("refundedAt", LocalDateTime.now().toString());
                    sale.setTransactionDetails(details);
                    sale.setStatus(TicketSaleStatus.REFUNDED);
                    TicketSale saved = ticketSaleRepository.save(sale);
                    notifyObservers("REFUNDED", buildAuditPayload(saved, Map.of("refundReason", event.reason())));
                    publishPaymentRefunded(saved);
                });
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

    private BookingDTO fetchBooking(Long bookingId) {
        if (bookingServiceClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Booking service client is not configured");
        }
        try {
            return bookingServiceClient.getBooking(bookingId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Booking service unavailable", exception);
        }
    }

    private EventDTO fetchEvent(Long eventId) {
        if (eventServiceClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event service client is not configured");
        }
        try {
            return eventServiceClient.getEvent(eventId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Event service unavailable", exception);
        }
    }

    private void fetchUser(Long userId) {
        if (userServiceClient == null) {
            return;
        }
        try {
            userServiceClient.getUser(userId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found", exception);
        }
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
        fetchUser(userId);

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

        BookingDTO booking = fetchBooking(sale.getBookingId());
        if (booking.eventId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking has no associated event");
        }
        EventDTO event = fetchEvent(booking.eventId());
        if (event.eventDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "booking has no associated event");
        }

        LocalDateTime eventDate = event.eventDate();
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
        redisCacheService.delete("sales-service::S5-F11::" + saleId);
        redisCacheService.delete("sales-service::ticket-sale::" + saleId);
    }

    private void publishPaymentInitiated(TicketSale sale) {
        if (paymentEventPublisher != null) {
            paymentEventPublisher.publishPaymentInitiated(new PaymentInitiatedEvent(
                    sale.getId(),
                    sale.getBookingId(),
                    toBigDecimal(sale.getAmount())));
        }
    }

    private void publishPaymentCompleted(TicketSale sale) {
        if (paymentEventPublisher != null) {
            paymentEventPublisher.publishPaymentCompleted(new PaymentCompletedEvent(
                    sale.getId(),
                    sale.getBookingId(),
                    toBigDecimal(sale.getAmount())));
        }
    }

    private void publishPaymentFailed(TicketSale sale, String reason) {
        if (paymentEventPublisher != null) {
            paymentEventPublisher.publishPaymentFailed(new PaymentFailedEvent(sale.getId(), sale.getBookingId(), reason));
        }
    }

    private void publishPaymentRefunded(TicketSale sale) {
        if (paymentEventPublisher != null) {
            paymentEventPublisher.publishPaymentRefunded(new PaymentRefundedEvent(
                    sale.getId(),
                    sale.getBookingId(),
                    toBigDecimal(sale.getAmount())));
        }
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }




}
