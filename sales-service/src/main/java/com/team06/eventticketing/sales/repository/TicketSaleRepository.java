package com.team06.eventticketing.sales.repository;

import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {

    List<TicketSale> findByBookingId(Long bookingId);

    List<TicketSale> findByUserId(Long userId);

    List<TicketSale> findByStatus(TicketSaleStatus status);

    @Query("""
            SELECT DISTINCT ts
            FROM TicketSale ts
            LEFT JOIN FETCH ts.salePromotions sp
            LEFT JOIN FETCH sp.promotion
            WHERE ts.id = :id
            """)
    Optional<TicketSale> findByIdWithSalePromotions(@Param("id") Long id);
}
