package com.team06.eventticketing.booking.controller;

import com.team06.eventticketing.common.cache.CachedDetail;
import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.cache.InvalidateServiceCaches;
import com.team06.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team06.eventticketing.booking.dto.BookingDetailsDTO;
import com.team06.eventticketing.booking.dto.BookingEstimateRequest;
import com.team06.eventticketing.booking.dto.BookingItemRequest;
import com.team06.eventticketing.booking.dto.BookingRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.service.BookingService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.team06.eventticketing.booking.dto.BookingAnalyticsDTO;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingService.getAllBookings();
    }

    @GetMapping("/{id}")
    @CachedDetail(service = "booking-service", entity = "booking", key = "#id", ttlSeconds = 900)
    public Booking getBookingById(@PathVariable Long id) {
        return bookingService.getBookingById(id);
    }

    @GetMapping("/{bookingId}/details")
    @CachedFeature(service = "booking-service", featureId = "S3-F3", ttlSeconds = 300)
    public BookingDetailsDTO getBookingDetails(@PathVariable Long bookingId) {
        return bookingService.getBookingDetails(bookingId);
    }

    @GetMapping("/estimate")
    @CachedFeature(service = "booking-service", featureId = "S3-F3", ttlSeconds = 300)
    public BookingCostEstimateDTO estimateBookingCost(
            @RequestParam Long eventId,
            @RequestParam Integer ticketCount,
            @RequestParam String ticketTier
    ) {
        return bookingService.estimateBookingCost(new BookingEstimateRequest(eventId, ticketCount, ticketTier));
    }

    @PostMapping("/estimate")
    @CachedFeature(service = "booking-service", featureId = "S3-F3", ttlSeconds = 300)
    public BookingCostEstimateDTO estimateBookingCost(@RequestBody BookingEstimateRequest request) {
        return bookingService.estimateBookingCost(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking createBooking(@RequestBody BookingRequest request) {
        return bookingService.createBooking(request);
    }

    @PutMapping("/{id}")
    @InvalidateServiceCaches(
            service = "booking-service",
            featurePrefix = "S3-",
            detailKeys = {"'booking-service::booking::' + #id"})
    public Booking updateBooking(@PathVariable Long id, @RequestBody BookingRequest request) {
        return bookingService.updateBooking(id, request);
    }

    @PutMapping("/{id}/complete")
    @InvalidateServiceCaches(
            service = "booking-service",
            featurePrefix = "S3-",
            detailKeys = {"'booking-service::booking::' + #id"})
    public Booking completeBooking(@PathVariable Long id) {
        return bookingService.completeBooking(id);
    }

    @GetMapping("/search")
    @CachedFeature(service = "booking-service", featureId = "S3-F1", ttlSeconds = 300)
    public List<Booking> searchBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return bookingService.searchBookings(status, startDate, endDate);
    }

    @GetMapping("/analytics")
    @CachedFeature(service = "booking-service", featureId = "S3-F6", ttlSeconds = 600)
    public BookingAnalyticsDTO getBookingAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return bookingService.getBookingAnalytics(startDate, endDate);
    }

    @GetMapping("/metadata/search")
    @CachedFeature(service = "booking-service", featureId = "S3-F5", ttlSeconds = 300)
    public List<Booking> searchBookingsByMetadata(
            @RequestParam String key,
            @RequestParam String value
    ) {
        return bookingService.searchBookingsByMetadata(key, value);
    }

    @PostMapping("/{bookingId}/items")
    @InvalidateServiceCaches(
            service = "booking-service",
            featurePrefix = "S3-",
            detailKeys = {"'booking-service::booking::' + #bookingId"})
    public Booking addItemsToBooking(@PathVariable Long bookingId, @RequestBody List<BookingItemRequest> items) {
        return bookingService.addItemsToBooking(bookingId, items);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @InvalidateServiceCaches(
            service = "booking-service",
            featurePrefix = "S3-",
            detailKeys = {"'booking-service::booking::' + #id"})
    public void deleteBooking(@PathVariable Long id) {
        bookingService.deleteBooking(id);
    }
    @PutMapping("/{id}/cancel")
    @InvalidateServiceCaches(
            service = "booking-service",
            featurePrefix = "S3-",
            detailKeys = {"'booking-service::booking::' + #id"})
    public Booking cancelBooking(@PathVariable Long id) {
        return bookingService.cancelBooking(id);
    }
    @PutMapping("/{bookingId}/confirm")
    @InvalidateServiceCaches(
            service = "booking-service",
            featurePrefix = "S3-",
            detailKeys = {"'booking-service::booking::' + #bookingId"})
    public Booking confirmBooking(
            @PathVariable Long bookingId,
            @RequestParam Long eventId) {
        return bookingService.confirmBooking(bookingId, eventId);
    }


}
