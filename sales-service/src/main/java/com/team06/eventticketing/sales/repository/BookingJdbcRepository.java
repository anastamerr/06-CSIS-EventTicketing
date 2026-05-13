package com.team06.eventticketing.sales.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingJdbcRepository {

    public BookingJdbcRepository(JdbcTemplate jdbcTemplate) {
    }

    public Optional<BookingPaymentRow> findByIdForUpdate(Long bookingId) {
        return Optional.empty();
    }

    public Optional<SaleEventDateRow> findEventDateByTicketSaleId(Long saleId) {
        return Optional.empty();
    }

    public record BookingPaymentRow(Long id, String status, Double totalAmount) {
    }

    public record SaleEventDateRow(Long eventId, LocalDateTime eventDate) {
    }
}
