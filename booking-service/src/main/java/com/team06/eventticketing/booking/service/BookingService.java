package com.team06.eventticketing.booking.service;

import com.team06.eventticketing.booking.adapter.BookingAnalyticsAdapter;
import com.team06.eventticketing.booking.dto.BookingAnalyticsDTO;
import com.team06.eventticketing.booking.dto.BookingAnalyticsDashboardDTO;
import com.team06.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team06.eventticketing.booking.dto.BookingDetailsDTO;
import com.team06.eventticketing.booking.dto.BookingDetailsItemDTO;
import com.team06.eventticketing.booking.dto.BookingEstimateRequest;
import com.team06.eventticketing.booking.dto.BookingItemRequest;
import com.team06.eventticketing.booking.dto.BookingRequest;
import com.team06.eventticketing.booking.messaging.BookingEventPublisher;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.contracts.dto.AvgCapacityDTO;
import com.team06.eventticketing.contracts.dto.BookingDTO;
import com.team06.eventticketing.contracts.dto.BookingSummaryDTO;
import com.team06.eventticketing.contracts.dto.EventBookingRevenueDTO;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.events.BookingCancelledEvent;
import com.team06.eventticketing.contracts.events.BookingCompletedEvent;
import com.team06.eventticketing.contracts.events.BookingPlacedEvent;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
import com.team06.eventticketing.contracts.feign.TicketServiceClient;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import feign.FeignException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingAnalyticsAdapter bookingAnalyticsAdapter;
    private final ObjectProvider<BookingService> selfProvider;
    private final EventServiceClient eventServiceClient;
    private final UserServiceClient userServiceClient;
    private final TicketServiceClient ticketServiceClient;
    private final BookingEventPublisher bookingEventPublisher;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public BookingService(
            BookingRepository bookingRepository,
            BookingAnalyticsAdapter bookingAnalyticsAdapter,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory,
            ObjectProvider<BookingService> selfProvider,
            EventServiceClient eventServiceClient,
            UserServiceClient userServiceClient,
            TicketServiceClient ticketServiceClient,
            BookingEventPublisher bookingEventPublisher
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingAnalyticsAdapter = bookingAnalyticsAdapter;
        this.selfProvider = selfProvider;
        this.eventServiceClient = eventServiceClient;
        this.userServiceClient = userServiceClient;
        this.ticketServiceClient = ticketServiceClient;
        this.bookingEventPublisher = bookingEventPublisher;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public BookingService(
            BookingRepository bookingRepository,
            EventServiceClient eventServiceClient,
            UserServiceClient userServiceClient,
            TicketServiceClient ticketServiceClient,
            BookingEventPublisher bookingEventPublisher
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingAnalyticsAdapter = new BookingAnalyticsAdapter();
        this.selfProvider = null;
        this.eventServiceClient = eventServiceClient;
        this.userServiceClient = userServiceClient;
        this.ticketServiceClient = ticketServiceClient;
        this.bookingEventPublisher = bookingEventPublisher;
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAllWithBookingItems();
    }

    @Transactional(readOnly = true)
    public BookingCostEstimateDTO estimateBookingCost(BookingEstimateRequest request) {
        validateEstimateRequest(request);
        AvgCapacityDTO capacity = getAverageCapacity(request.eventId());
        Double averageSessionCapacity = capacity.avgCapacity();
        if (averageSessionCapacity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event sessions not found");
        }

        double basePrice = averageSessionCapacity / 10.0;
        double tierMultiplier = "VIP".equalsIgnoreCase(request.ticketTier()) ? 2.5 : 1.0;
        double ticketCost = basePrice * tierMultiplier * request.ticketCount();
        double serviceFee = ticketCost * 0.15;
        double demandMultiplier = resolveDemandMultiplier(bookingRepository.countActiveBookingsByEventId(
                request.eventId()
        ));
        double estimatedTotal = (ticketCost + serviceFee) * demandMultiplier;

        return BookingCostEstimateDTO.builder()
                .ticketCost(ticketCost)
                .serviceFee(serviceFee)
                .demandMultiplier(demandMultiplier)
                .estimatedTotal(estimatedTotal)
                .build();
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findByIdWithBookingItems(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    @Transactional(readOnly = true)
    public BookingDetailsDTO getBookingDetails(Long id) {
        Booking booking = getBookingById(id);

        List<BookingDetailsItemDTO> items = booking.getBookingItems().stream()
                .sorted(Comparator.comparing(BookingItem::getEventOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(this::toBookingDetailsItemDTO)
                .toList();

        int confirmedItems = (int) booking.getBookingItems().stream()
                .filter(item -> item.getStatus() == BookingItemStatus.CONFIRMED)
                .count();

        return BookingDetailsDTO.builder()
                .bookingId(booking.getId())
                .userId(booking.getUserId())
                .eventId(booking.getEventId())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .metadata(booking.getMetadata())
                .items(items)
                .totalItems(items.size())
                .confirmedItems(confirmedItems)
                .build();
    }

    @Transactional
    public Booking createBooking(BookingRequest request) {
        Booking booking = new Booking();
        applyRequest(booking, request, false);
        Booking saved = bookingRepository.save(booking);
        notifyObservers("BOOKING_CREATED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional
    public Booking updateBooking(Long id, BookingRequest request) {
        Booking existing = getBookingById(id);
        applyRequest(existing, request, true);
        Booking saved = bookingRepository.save(existing);
        notifyObservers("BOOKING_UPDATED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional
    public Booking completeBooking(Long id) {
        Booking booking = getBookingByIdForUpdate(id);
        validateCompletableBooking(booking);

        double totalAmount = booking.getTotalAmount() == null ? calculateTotalAmount(booking) : booking.getTotalAmount();
        validateSagaPreChecks(booking);

        booking.setTotalAmount(totalAmount);
        booking.setStatus(BookingStatus.COMPLETING);

        Booking saved = bookingRepository.save(booking);
        bookingEventPublisher.publishBookingCompleted(new BookingCompletedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getEventId(),
                BigDecimal.valueOf(totalAmount)));
        notifyObservers("BOOKING_COMPLETED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional
    public Booking markPaymentInitiated(Long bookingId) {
        Booking booking = getBookingByIdForUpdate(bookingId);
        if (booking.getStatus() == BookingStatus.PAYMENT_PENDING
                || booking.getStatus() == BookingStatus.PAID
                || booking.getStatus() == BookingStatus.PAYMENT_FAILED
                || booking.getStatus() == BookingStatus.REFUNDED) {
            return booking;
        }
        if (booking.getStatus() != BookingStatus.COMPLETING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not awaiting payment initiation");
        }
        booking.setStatus(BookingStatus.PAYMENT_PENDING);
        Booking saved = bookingRepository.save(booking);
        notifyObservers("PAYMENT_INITIATED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional
    public Booking markPaymentCompleted(Long bookingId) {
        Booking booking = getBookingByIdForUpdate(bookingId);
        if (booking.getStatus() == BookingStatus.PAID) {
            return booking;
        }
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not pending payment");
        }
        booking.setStatus(BookingStatus.PAID);
        Booking saved = bookingRepository.save(booking);
        notifyObservers("PAYMENT_COMPLETED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional
    public Booking markPaymentFailed(Long bookingId, String reason) {
        Booking booking = getBookingByIdForUpdate(bookingId);
        if (booking.getStatus() == BookingStatus.PAYMENT_FAILED
                || booking.getStatus() == BookingStatus.REFUNDED) {
            return booking;
        }
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not pending payment");
        }
        booking.setStatus(BookingStatus.PAYMENT_FAILED);
        Booking saved = bookingRepository.save(booking);
        bookingEventPublisher.publishBookingCancelled(new BookingCancelledEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getEventId(),
                reason == null || reason.isBlank() ? "payment_failed" : reason));
        notifyObservers("PAYMENT_FAILED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional
    public Booking markPaymentRefunded(Long bookingId) {
        Booking booking = getBookingByIdForUpdate(bookingId);
        if (booking.getStatus() == BookingStatus.REFUNDED) {
            return booking;
        }
        if (booking.getStatus() != BookingStatus.PAYMENT_FAILED && booking.getStatus() != BookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not refundable");
        }
        booking.setStatus(BookingStatus.REFUNDED);
        Booking saved = bookingRepository.save(booking);
        notifyObservers("PAYMENT_REFUNDED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    public List<Booking> searchBookings(BookingStatus status, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be on or before endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        if (status != null) {
            return bookingRepository.findByStatusAndBookingDateBetweenOrderByBookingDateDesc(
                    status, startDateTime, endDateTime
            );
        }
        return bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(startDateTime, endDateTime);
    }

    @Transactional(readOnly = true)
    public BookingAnalyticsDTO getBookingAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be on or before endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Object[]> rows = bookingRepository.findAnalyticsByDateRange(startDateTime, endDateTime);
        if (rows.isEmpty()) {
            return BookingAnalyticsDTO.builder().build();
        }
        return bookingAnalyticsAdapter.adapt(rows.get(0));
    }

    @Transactional(readOnly = true)
    public BookingAnalyticsDashboardDTO getBookingAnalyticsDashboard(LocalDate startDate, LocalDate endDate) {
        validateAnalyticsDateRange(startDate, endDate);

        notifyObservers("ANALYTICS_VIEWED", Map.of(
                "details", Map.of(
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString())));

        return self().getCachedBookingAnalyticsDashboard(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));
    }

    @Transactional(readOnly = true)
    @CachedFeature(service = "booking-service", featureId = "S3-F10", ttlSeconds = 600)
    public BookingAnalyticsDashboardDTO getCachedBookingAnalyticsDashboard(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        List<Object[]> rows = bookingRepository.findDashboardAnalytics(startDateTime, endDateTime);
        return buildDashboardAnalytics(rows);
    }

    @Transactional(readOnly = true)
    public List<Booking> searchBookingsByMetadata(String key, String value) {
        validateMetadataSearchParams(key, value);
        return bookingRepository.findByMetadataField(key.trim(), value.trim());
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking booking = getBookingByIdForUpdate(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only pending or confirmed bookings can be cancelled"
            );
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);
        bookingEventPublisher.publishBookingCancelled(new BookingCancelledEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getEventId(),
                "user_requested"));
        notifyObservers("BOOKING_CANCELLED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional
    public Booking confirmBooking(Long bookingId, Long eventId) {
        Booking booking = getBookingByIdForUpdate(bookingId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending bookings can be confirmed");
        }

        EventDTO event = getEvent(eventId);
        if (!"UPCOMING".equalsIgnoreCase(event.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event must be UPCOMING to confirm a booking");
        }

        booking.setEventId(eventId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        bookingEventPublisher.publishBookingPlaced(new BookingPlacedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getEventId()));
        notifyObservers("BOOKING_CONFIRMED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional(readOnly = true)
    public BookingSummaryDTO getUserBookingSummary(Long userId) {
        long totalBookings = bookingRepository.countByUserId(userId);
        long completedBookings = bookingRepository.countByUserIdAndStatus(userId, BookingStatus.COMPLETED);
        long cancelledBookings = bookingRepository.countByUserIdAndStatus(userId, BookingStatus.CANCELLED);
        BigDecimal totalSpent = nullToZero(bookingRepository.sumCompletedAmountByUserId(userId));
        BigDecimal average = completedBookings == 0
                ? BigDecimal.ZERO
                : totalSpent.divide(BigDecimal.valueOf(completedBookings), 2, RoundingMode.HALF_UP);
        return new BookingSummaryDTO(totalBookings, completedBookings, cancelledBookings, totalSpent, average);
    }

    @Transactional(readOnly = true)
    public int getUserActiveBookingCount(Long userId) {
        return Math.toIntExact(bookingRepository.countActiveBookingsByUserId(userId));
    }

    @Transactional(readOnly = true)
    public long getUserBookingCount(Long userId, BookingStatus status) {
        if (status == null) {
            return bookingRepository.countByUserId(userId);
        }
        return bookingRepository.countByUserIdAndStatus(userId, status);
    }

    @Transactional(readOnly = true)
    public BigDecimal getUserCompletedBookingTotal(Long userId, LocalDate startDate, LocalDate endDate) {
        validateAnalyticsDateRange(startDate, endDate);
        return nullToZero(bookingRepository.sumCompletedAmountByUserIdAndDateRange(
                userId,
                startDate.atStartOfDay(),
                endDate.atTime(LocalTime.MAX)));
    }

    @Transactional(readOnly = true)
    public EventBookingRevenueDTO getEventRevenue(Long eventId, LocalDate startDate, LocalDate endDate) {
        validateAnalyticsDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        long totalBookings = bookingRepository.countByEventIdAndBookingDateBetween(eventId, start, end);
        long completedBookings = bookingRepository.countByEventIdAndStatusAndBookingDateBetween(
                eventId,
                BookingStatus.COMPLETED,
                start,
                end);
        BigDecimal totalRevenue = nullToZero(bookingRepository.sumCompletedAmountByEventIdAndDateRange(
                eventId,
                start,
                end));
        BigDecimal average = completedBookings == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(completedBookings), 2, RoundingMode.HALF_UP);
        return new EventBookingRevenueDTO(totalBookings, totalRevenue.doubleValue(), average.doubleValue());
    }

    @Transactional(readOnly = true)
    public int getEventActiveBookingCount(Long eventId) {
        return Math.toIntExact(bookingRepository.countActiveBookingsByEventId(eventId));
    }

    @Transactional(readOnly = true)
    public BookingDTO getBookingContract(Long bookingId) {
        Booking booking = getBookingById(bookingId);
        return new BookingDTO(
                booking.getId(),
                booking.getUserId(),
                booking.getEventId(),
                booking.getStatus() == null ? null : booking.getStatus().name(),
                booking.getTotalAmount());
    }

    @Transactional
    public Booking addItemsToBooking(Long bookingId, List<BookingItemRequest> items) {
        Booking booking = getBookingByIdForUpdate(bookingId);
        validateAppendableBooking(booking);
        validateBookingItemsRequest(items);

        int nextEventOrder = booking.getBookingItems().stream()
                .map(BookingItem::getEventOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        for (BookingItemRequest itemRequest : items) {
            nextEventOrder++;
            BookingItem bookingItem = new BookingItem();
            bookingItem.setEventOrder(nextEventOrder);
            bookingItem.setSessionId(itemRequest.getSessionId());
            bookingItem.setSessionTitle(itemRequest.getSessionTitle().trim());
            bookingItem.setQuantity(itemRequest.getQuantity());
            bookingItem.setUnitPrice(itemRequest.getUnitPrice());
            bookingItem.setStatus(BookingItemStatus.RESERVED);
            bookingItem.setMetadata(itemRequest.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(itemRequest.getMetadata()));
            booking.addBookingItem(bookingItem);
        }
        booking.setTotalAmount(calculateTotalAmount(booking));

        Booking saved = bookingRepository.save(booking);
        notifyObservers("ITEMS_ADDED", Map.of(
                "bookingId", saved.getId(),
                "details", Map.of(
                        "itemCount", items.size(),
                        "status", saved.getStatus().name())));
        return saved;
    }

    public void deleteBooking(Long id) {
        Booking booking = getBookingById(id);
        notifyObservers("BOOKING_DELETED", Map.of(
                "bookingId", booking.getId(),
                "details", buildBookingDetails(booking)));
        bookingRepository.deleteById(id);
    }

    private Booking getBookingByIdForUpdate(Long id) {
        return bookingRepository.findByIdWithBookingItemsForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private void applyRequest(Booking booking, BookingRequest request, boolean merge) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        if (request.getUserId() != null || !merge) {
            booking.setUserId(request.getUserId());
        }
        if (request.getEventId() != null || !merge) {
            booking.setEventId(request.getEventId());
        }
        if (request.getContactEmail() != null || !merge) {
            booking.setContactEmail(request.getContactEmail());
        }
        if (request.getStatus() != null || !merge) {
            booking.setStatus(request.getStatus() == null ? BookingStatus.PENDING : request.getStatus());
        }
        if (request.getTotalAmount() != null || !merge) {
            booking.setTotalAmount(request.getTotalAmount());
        }
        if (request.getMetadata() != null || !merge) {
            booking.setMetadata(request.getMetadata() == null ? new LinkedHashMap<>() : request.getMetadata());
        }
        if (request.getBookingDate() != null || !merge) {
            booking.setBookingDate(request.getBookingDate());
        }
        if (request.getConfirmedAt() != null || !merge) {
            booking.setConfirmedAt(request.getConfirmedAt());
        }

        if (request.getBookingItems() != null) {
            List<BookingItem> existingItems = new ArrayList<>(booking.getBookingItems());
            for (BookingItem existingItem : existingItems) {
                booking.removeBookingItem(existingItem);
            }
            for (BookingItem item : request.getBookingItems()) {
                booking.addBookingItem(normalizeBookingItem(item));
            }
        }
    }

    private BookingItem normalizeBookingItem(BookingItem bookingItem) {
        if (bookingItem.getStatus() == null) {
            bookingItem.setStatus(BookingItemStatus.RESERVED);
        }
        if (bookingItem.getMetadata() == null) {
            bookingItem.setMetadata(new LinkedHashMap<>());
        }
        return bookingItem;
    }

    private BookingDetailsItemDTO toBookingDetailsItemDTO(BookingItem item) {
        return BookingDetailsItemDTO.builder()
                .id(item.getId())
                .eventOrder(item.getEventOrder())
                .sessionTitle(item.getSessionTitle())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .status(item.getStatus())
                .metadata(item.getMetadata())
                .build();
    }

    private void validateCompletableBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking must be checked in before completion"
            );
        }
    }

    private void validateSagaPreChecks(Booking booking) {
        EventDTO event = getEvent(booking.getEventId());
        if (!"ONGOING".equalsIgnoreCase(event.status()) && !"COMPLETED".equalsIgnoreCase(event.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event has not yet occurred or was cancelled");
        }

        UserDTO user = getUser(booking.getUserId());
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not active");
        }

        int usedTicketCount = getActiveTicketCountForBooking(booking.getId());
        if (usedTicketCount < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No attendance recorded for this booking");
        }
    }

    private double calculateTotalAmount(Booking booking) {
        double totalAmount = 0.0;

        for (BookingItem item : booking.getBookingItems()) {
            totalAmount += safeQuantity(item) * safeUnitPrice(item);
        }

        return totalAmount;
    }

    private void validateEstimateRequest(BookingEstimateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.eventId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId is required");
        }
        if (request.ticketCount() == null || request.ticketCount() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticketCount must be at least 1");
        }
        if (request.ticketTier() == null || request.ticketTier().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticketTier is required");
        }
        if (!"VIP".equalsIgnoreCase(request.ticketTier()) && !"standard".equalsIgnoreCase(request.ticketTier())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticketTier must be either VIP or standard");
        }
    }

    private void validateAppendableBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.CONFIRMED
                && booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot add items to a booking with status " + booking.getStatus()
            );
        }
    }

    private void validateBookingItemsRequest(List<BookingItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Items list is required");
        }

        for (BookingItemRequest item : items) {
            if (item.getSessionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required for all items");
            }
            if (item.getSessionTitle() == null || item.getSessionTitle().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionTitle is required for all items");
            }
            if (item.getQuantity() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity is required for all items");
            }
            if (item.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
            }
            if (item.getUnitPrice() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitPrice is required for all items");
            }
            if (item.getUnitPrice() <= 0.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitPrice must be greater than zero");
            }
        }
    }

    private void validateMetadataSearchParams(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key is required");
        }
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value is required");
        }
    }

    private void validateAnalyticsDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be on or before endDate");
        }
    }

    private AvgCapacityDTO getAverageCapacity(Long eventId) {
        try {
            return eventServiceClient.getEventAvgCapacity(eventId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event service temporarily unavailable", exception);
        }
    }

    private EventDTO getEvent(Long eventId) {
        try {
            return eventServiceClient.getEvent(eventId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event service temporarily unavailable", exception);
        }
    }

    private UserDTO getUser(Long userId) {
        try {
            return userServiceClient.getUser(userId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable", exception);
        }
    }

    private int getActiveTicketCountForBooking(Long bookingId) {
        try {
            return ticketServiceClient.getActiveTicketCountForBooking(bookingId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tickets not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ticket service temporarily unavailable", exception);
        }
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BookingAnalyticsDashboardDTO buildDashboardAnalytics(List<Object[]> rows) {
        Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            bookingsByStatus.put(status.name(), 0L);
        }

        long totalBookings = 0L;
        double totalRevenue = 0.0;
        long completedCount = 0L;
        long convertedCount = 0L;

        for (Object[] row : rows) {
            totalBookings = longValue(row[0]);
            totalRevenue = doubleValue(row[1]);
            completedCount = longValue(row[2]);
            convertedCount = longValue(row[3]);

            if (row[4] != null) {
                bookingsByStatus.put(row[4].toString(), longValue(row[5]));
            }
        }

        double averageBookingValue = completedCount == 0L ? 0.0 : totalRevenue / completedCount;
        double conversionRate = totalBookings == 0L ? 0.0 : (double) convertedCount / totalBookings;

        return BookingAnalyticsDashboardDTO.builder()
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .averageBookingValue(averageBookingValue)
                .conversionRate(conversionRate)
                .bookingsByStatus(bookingsByStatus)
                .build();
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private double doubleValue(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private int safeQuantity(BookingItem item) {
        return item.getQuantity() == null ? 0 : item.getQuantity();
    }

    private double safeUnitPrice(BookingItem item) {
        return item.getUnitPrice() == null ? 0.0 : item.getUnitPrice();
    }

    private double resolveDemandMultiplier(long activeBookingsCount) {
        if (activeBookingsCount <= 50) {
            return 1.0;
        }
        if (activeBookingsCount <= 200) {
            return 1.25;
        }
        return 1.5;
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

    private BookingService self() {
        return selfProvider == null ? this : selfProvider.getObject();
    }

    private Map<String, Object> buildBookingDetails(Booking booking) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("userId", booking.getUserId());
        details.put("eventId", booking.getEventId());
        details.put("status", booking.getStatus() == null ? null : booking.getStatus().name());
        return details;
    }

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.BOOKING, "booking_events"));
        }
    }
}
