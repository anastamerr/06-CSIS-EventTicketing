package com.team06.eventticketing.sales.repository;

import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSaleRepository extends JpaRepository<TicketSale, Long> {

    List<TicketSale> findByBookingId(Long bookingId);

    List<TicketSale> findByUserId(Long userId);

    List<TicketSale> findByStatus(TicketSaleStatus status);
}
