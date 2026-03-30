package com.team06.eventticketing.sales.repository;

import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {

    List<TicketSale> findByBookingId(Long bookingId);

    boolean existsByBookingIdAndStatus(Long bookingId, TicketSaleStatus status);

    List<TicketSale> findByUserId(Long userId);

    List<TicketSale> findByStatus(TicketSaleStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ts
            FROM TicketSale ts
            WHERE ts.bookingId = :bookingId
            ORDER BY ts.id ASC
            """)
    List<TicketSale> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    @Query("""
            SELECT DISTINCT ts
            FROM TicketSale ts
            LEFT JOIN FETCH ts.salePromotions sp
            LEFT JOIN FETCH sp.promotion
            WHERE ts.id = :id
            """)
    Optional<TicketSale> findByIdWithSalePromotions(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT DISTINCT ts
            FROM TicketSale ts
            LEFT JOIN FETCH ts.salePromotions sp
            LEFT JOIN FETCH sp.promotion
            WHERE ts.id = :id
            """)
    Optional<TicketSale> findByIdWithSalePromotionsForUpdate(@Param("id") Long id);
    List<TicketSale> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

}
