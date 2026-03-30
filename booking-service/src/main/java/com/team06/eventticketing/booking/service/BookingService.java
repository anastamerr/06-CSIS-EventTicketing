package com.team06.eventticketing.booking.service;

import com.team06.eventticketing.booking.dto.BookingRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.booking.repository.TicketSaleJdbcRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private static final Set<String> SUPPORTED_PAYMENT_METHODS = Set.of("CREDIT_CARD", "DEBIT_CARD", "WALLET");

    private final BookingRepository bookingRepository;
    private final TicketSaleJdbcRepository ticketSaleJdbcRepository;

    public BookingService(BookingRepository bookingRepository, TicketSaleJdbcRepository ticketSaleJdbcRepository) {
        this.bookingRepository = bookingRepository;
        this.ticketSaleJdbcRepository = ticketSaleJdbcRepository;
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAllWithBookingItems();
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findByIdWithBookingItems(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
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

    public List<Booking> searchBookings(BookingStatus status, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }

        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        if (status != null) {
            return bookingRepository.findByStatusAndBookingDateBetweenOrderByBookingDateDesc(
                    status,
                    startDateTime,
                    endDateTime
            );
        }

        return bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(startDateTime, endDateTime);
    }
}
