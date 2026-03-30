package com.team06.eventticketing.booking.controller;

import com.team06.eventticketing.booking.dto.BookingItemRequest;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.service.BookingItemService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings/{bookingId}/items")
public class BookingItemController {

    private final BookingItemService bookingItemService;

    public BookingItemController(BookingItemService bookingItemService) {
        this.bookingItemService = bookingItemService;
    }

    @GetMapping
    public List<BookingItem> getBookingItems(@PathVariable Long bookingId) {
        return bookingItemService.getBookingItems(bookingId);
    }

    @GetMapping("/{itemId}")
    public BookingItem getBookingItem(@PathVariable Long bookingId, @PathVariable Long itemId) {
        return bookingItemService.getBookingItem(bookingId, itemId);
    }

    @PostMapping("/single")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingItem createBookingItem(@PathVariable Long bookingId, @RequestBody BookingItemRequest request) {
        return bookingItemService.createBookingItem(bookingId, request);
    }

    @PutMapping("/{itemId}")
    public BookingItem updateBookingItem(
            @PathVariable Long bookingId,
            @PathVariable Long itemId,
            @RequestBody BookingItemRequest request
    ) {
        return bookingItemService.updateBookingItem(bookingId, itemId, request);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBookingItem(@PathVariable Long bookingId, @PathVariable Long itemId) {
        bookingItemService.deleteBookingItem(bookingId, itemId);
    }
}
