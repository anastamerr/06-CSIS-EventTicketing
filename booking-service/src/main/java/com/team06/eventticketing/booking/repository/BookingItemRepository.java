package com.team06.eventticketing.booking.repository;

import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    List<BookingItem> findByBookingIdOrderByEventOrderAsc(Long bookingId);

    List<BookingItem> findByStatus(BookingItemStatus status);

    Optional<BookingItem> findByIdAndBookingId(Long id, Long bookingId);
}
