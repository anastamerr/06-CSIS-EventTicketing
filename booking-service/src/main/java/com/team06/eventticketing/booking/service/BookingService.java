package com.team06.eventticketing.booking.service;

import com.team06.eventticketing.booking.dto.BookingRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
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

    public void deleteBooking(Long id) {
        getBookingById(id);
        bookingRepository.deleteById(id);
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
}
