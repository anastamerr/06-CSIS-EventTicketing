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
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.booking.repository.TicketJdbcRepository;
import com.team06.eventticketing.booking.repository.TicketSaleJdbcRepository;
import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> SUPPORTED_PAYMENT_METHODS = Set.of("CREDIT_CARD", "DEBIT_CARD", "WALLET");

    private final BookingRepository bookingRepository;
    private final TicketJdbcRepository ticketJdbcRepository;
    private final TicketSaleJdbcRepository ticketSaleJdbcRepository;
    private final BookingAnalyticsAdapter bookingAnalyticsAdapter;
    private final ObjectProvider<BookingService> selfProvider;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public BookingService(
            BookingRepository bookingRepository,
            TicketJdbcRepository ticketJdbcRepository,
            TicketSaleJdbcRepository ticketSaleJdbcRepository,
            BookingAnalyticsAdapter bookingAnalyticsAdapter,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory,
            ObjectProvider<BookingService> selfProvider
    ) {
        this.bookingRepository = bookingRepository;
        this.ticketJdbcRepository = ticketJdbcRepository;
        this.ticketSaleJdbcRepository = ticketSaleJdbcRepository;
        this.bookingAnalyticsAdapter = bookingAnalyticsAdapter;
        this.selfProvider = selfProvider;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public BookingService(
            BookingRepository bookingRepository,
            TicketJdbcRepository ticketJdbcRepository,
            TicketSaleJdbcRepository ticketSaleJdbcRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.ticketJdbcRepository = ticketJdbcRepository;
        this.ticketSaleJdbcRepository = ticketSaleJdbcRepository;
        this.bookingAnalyticsAdapter = new BookingAnalyticsAdapter();
        this.selfProvider = null;
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAllWithBookingItems();
    }

    @Transactional(readOnly = true)
    public BookingCostEstimateDTO estimateBookingCost(BookingEstimateRequest request) {
        validateEstimateRequest(request);
        Double averageSessionCapacity = bookingRepository.findAverageSessionCapacityByEventId(request.eventId());
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
        booking.setTotalAmount(totalAmount);
        booking.setStatus(BookingStatus.COMPLETED);

        if (!ticketSaleJdbcRepository.existsByBookingId(id)) {
            ticketSaleJdbcRepository.createPendingSale(
                    booking.getId(),
                    booking.getUserId(),
                    totalAmount,
                    resolvePaymentMethod(booking),
                    buildTransactionDetails(booking, totalAmount)
            );
        }

        Booking saved = bookingRepository.save(booking);
        notifyObservers("BOOKING_COMPLETED", Map.of(
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
                    "Only PENDING or CONFIRMED bookings can be cancelled"
            );
        }

        ticketJdbcRepository.cancelValidTicketsForBooking(bookingId);
        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);
        notifyObservers("BOOKING_CANCELLED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
    }

    @Transactional
    public Booking confirmBooking(Long bookingId, Long eventId) {
        Booking booking = getBookingByIdForUpdate(bookingId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING bookings can be confirmed");
        }

        List<Object[]> eventRows = bookingRepository.findEventById(eventId);
        if (eventRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }

        Object[] eventRow = eventRows.get(0);
        String eventStatus = eventRow[1] == null ? null : eventRow[1].toString();
        if (!"UPCOMING".equals(eventStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event must be UPCOMING to confirm a booking");
        }

        booking.setEventId(eventId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        notifyObservers("BOOKING_CONFIRMED", Map.of(
                "bookingId", saved.getId(),
                "details", buildBookingDetails(saved)));
        return saved;
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

    private Map<String, Object> buildTransactionDetails(Booking booking, double totalAmount) {
        Map<String, Object> transactionDetails = new LinkedHashMap<>();
        transactionDetails.put("bookingTotalAmount", totalAmount);
        return transactionDetails;
    }

    private String resolvePaymentMethod(Booking booking) {
        if (booking.getMetadata() == null) {
            return "WALLET";
        }

        Object paymentMethod = booking.getMetadata().get("paymentMethod");
        if (!(paymentMethod instanceof String paymentMethodValue)) {
            return "WALLET";
        }

        String normalized = paymentMethodValue.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_PAYMENT_METHODS.contains(normalized) ? normalized : "WALLET";
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
