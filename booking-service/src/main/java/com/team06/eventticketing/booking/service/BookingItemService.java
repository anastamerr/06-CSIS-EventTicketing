package com.team06.eventticketing.booking.service;

import com.team06.eventticketing.common.observer.EntityObserver;
import com.team06.eventticketing.common.observer.EventFactory;
import com.team06.eventticketing.common.observer.EventType;
import com.team06.eventticketing.common.observer.MongoEventLogger;
import com.team06.eventticketing.booking.dto.BookingItemRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.repository.BookingItemRepository;
import com.team06.eventticketing.booking.repository.BookingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingItemService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public BookingItemService(
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            MongoTemplate mongoTemplate,
            EventFactory eventFactory
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        registerObserverIfAvailable(mongoTemplate, eventFactory);
    }

    public BookingItemService(BookingRepository bookingRepository, BookingItemRepository bookingItemRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
    }

    public List<BookingItem> getBookingItems(Long bookingId) {
        getBookingById(bookingId);
        return bookingItemRepository.findByBookingIdOrderByEventOrderAsc(bookingId);
    }

    public BookingItem getBookingItem(Long bookingId, Long itemId) {
        getBookingById(bookingId);
        return bookingItemRepository.findByIdAndBookingId(itemId, bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking item not found"));
    }

    @Transactional
    public BookingItem createBookingItem(Long bookingId, BookingItemRequest request) {
        Booking booking = getBookingById(bookingId);
        BookingItem bookingItem = new BookingItem();
        applyRequest(bookingItem, request, booking);
        BookingItem saved = bookingItemRepository.save(bookingItem);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("itemId", saved.getId());
        details.put("sessionId", saved.getSessionId());
        notifyObservers("BOOKING_ITEM_CREATED", Map.of(
                "bookingId", bookingId,
                "details", details));
        return saved;
    }

    @Transactional
    public BookingItem updateBookingItem(Long bookingId, Long itemId, BookingItemRequest request) {
        Booking booking = getBookingById(bookingId);
        BookingItem existing = bookingItemRepository.findByIdAndBookingId(itemId, bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking item not found"));
        applyRequest(existing, request, booking);
        BookingItem saved = bookingItemRepository.save(existing);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("itemId", saved.getId());
        details.put("sessionId", saved.getSessionId());
        notifyObservers("BOOKING_ITEM_UPDATED", Map.of(
                "bookingId", bookingId,
                "details", details));
        return saved;
    }

    public void deleteBookingItem(Long bookingId, Long itemId) {
        BookingItem item = getBookingItem(bookingId, itemId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("itemId", item.getId());
        details.put("sessionId", item.getSessionId());
        notifyObservers("BOOKING_ITEM_DELETED", Map.of(
                "bookingId", bookingId,
                "details", details));
        bookingItemRepository.deleteById(itemId);
    }

    private Booking getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private void applyRequest(BookingItem bookingItem, BookingItemRequest request, Booking booking) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.getEventOrder() != null) {
            bookingItem.setEventOrder(request.getEventOrder());
        }
        if (request.getSessionId() != null) {
            bookingItem.setSessionId(request.getSessionId());
        }
        if (request.getSessionTitle() != null) {
            bookingItem.setSessionTitle(request.getSessionTitle());
        }
        if (request.getQuantity() != null) {
            bookingItem.setQuantity(request.getQuantity());
        }
        if (request.getUnitPrice() != null) {
            bookingItem.setUnitPrice(request.getUnitPrice());
        }
        if (request.getStatus() != null || bookingItem.getStatus() == null) {
            bookingItem.setStatus(request.getStatus() == null ? BookingItemStatus.RESERVED : request.getStatus());
        }
        if (request.getMetadata() != null || bookingItem.getMetadata() == null) {
            bookingItem.setMetadata(request.getMetadata() == null ? new LinkedHashMap<>() : request.getMetadata());
        }
        bookingItem.setBooking(booking);
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

    private void registerObserverIfAvailable(@Nullable MongoTemplate mongoTemplate, @Nullable EventFactory eventFactory) {
        if (mongoTemplate != null && eventFactory != null) {
            register(new MongoEventLogger(mongoTemplate, eventFactory, EventType.BOOKING, "booking_events"));
        }
    }
}
