package com.team06.eventticketing.booking.repository;

import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByEventId(Long eventId);

    List<Booking> findByStatus(BookingStatus status);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.eventId = :eventId AND b.status IN (com.team06.eventticketing.booking.model.BookingStatus.PENDING, com.team06.eventticketing.booking.model.BookingStatus.CONFIRMED, com.team06.eventticketing.booking.model.BookingStatus.CHECKED_IN)")
    long countActiveBookingsByEventId(@Param("eventId") Long eventId);

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.bookingItems WHERE b.id = :id")
    Optional<Booking> findByIdWithBookingItems(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.bookingItems WHERE b.id = :id")
    Optional<Booking> findByIdWithBookingItemsForUpdate(@Param("id") Long id);

    @Query("SELECT DISTINCT b FROM Booking b LEFT JOIN FETCH b.bookingItems")
    List<Booking> findAllWithBookingItems();
}
