package com.team06.eventticketing.booking.service;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private static final Set<String> SUPPORTED_PAYMENT_METHODS = Set.of("CREDIT_CARD", "DEBIT_CARD", "WALLET");

    private final BookingRepository bookingRepository;
    private final TicketJdbcRepository ticketJdbcRepository;
    private final TicketSaleJdbcRepository ticketSaleJdbcRepository;

    public BookingService(
            BookingRepository bookingRepository,
            TicketJdbcRepository ticketJdbcRepository,
            TicketSaleJdbcRepository ticketSaleJdbcRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.ticketJdbcRepository = ticketJdbcRepository;
        this.ticketSaleJdbcRepository = ticketSaleJdbcRepository;
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
        double demandMultiplier = resolveDemandMultiplier(bookingRepository.countActiveBookingsByEventId(request.eventId()));
        double estimatedTotal = (ticketCost + serviceFee) * demandMultiplier;

        return new BookingCostEstimateDTO(ticketCost, serviceFee, demandMultiplier, estimatedTotal);
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

        return new BookingDetailsDTO(
                booking.getId(),
                booking.getUserId(),
                booking.getEventId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getMetadata(),
                items,
                items.size(),
                confirmedItems
        );
    }

    @Transactional
    public Booking createBooking(BookingRequest request) {
        Booking booking = new Booking();
        applyRequest(booking, request, false);
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking updateBooking(Long id, BookingRequest request) {
        Booking existing = getBookingById(id);
        applyRequest(existing, request, true);
        return bookingRepository.save(existing);
    }

    @Transactional
    public Booking completeBooking(Long id) {
        Booking booking = getBookingByIdForUpdate(id);
        validateCompletableBooking(booking);

        if (ticketSaleJdbcRepository.existsByBookingId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale already exists for booking");
        }

        double totalAmount = booking.getTotalAmount() == null ? calculateTotalAmount(booking) : booking.getTotalAmount();
        booking.setTotalAmount(totalAmount);
        booking.setStatus(BookingStatus.COMPLETED);

        ticketSaleJdbcRepository.createPendingSale(
                booking.getId(),
                booking.getUserId(),
                totalAmount,
                resolvePaymentMethod(booking),
                buildTransactionDetails(booking, totalAmount)
        );

        return bookingRepository.save(booking);
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
        return bookingRepository.save(booking);
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

        return bookingRepository.save(booking);
    }

    public void deleteBooking(Long id) {
        getBookingById(id);
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
        return new BookingDetailsItemDTO(
                item.getId(),
                item.getEventOrder(),
                item.getSessionTitle(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getStatus(),
                item.getMetadata()
        );
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
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
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
}
