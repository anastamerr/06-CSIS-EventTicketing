package com.team06.eventticketing.sales.service;

import com.team06.eventticketing.sales.dto.ProcessBookingSaleRequest;
import com.team06.eventticketing.sales.dto.RefundRequest;
import com.team06.eventticketing.sales.dto.RevenueReportDTO;
import com.team06.eventticketing.sales.dto.SaleDetailsDTO;
import com.team06.eventticketing.sales.dto.TicketSaleRequest;
import com.team06.eventticketing.sales.dto.TicketSaleResponse;
import com.team06.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team06.eventticketing.sales.model.SalePromotion;
import com.team06.eventticketing.sales.model.TicketSale;
import com.team06.eventticketing.sales.model.TicketSaleStatus;
import com.team06.eventticketing.sales.repository.BookingJdbcRepository;
import com.team06.eventticketing.sales.repository.TicketSaleRepository;
import com.team06.eventticketing.sales.repository.UserJdbcRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketSaleService {

    private final TicketSaleRepository ticketSaleRepository;
    private final BookingJdbcRepository bookingJdbcRepository;
    private final UserJdbcRepository userJdbcRepository;

    public TicketSaleService(
            TicketSaleRepository ticketSaleRepository,
            BookingJdbcRepository bookingJdbcRepository,
            UserJdbcRepository userJdbcRepository
    ) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.bookingJdbcRepository = bookingJdbcRepository;
        this.userJdbcRepository = userJdbcRepository;
    }

    @Transactional(readOnly = true)
    public List<TicketSaleResponse> getAllTicketSales() {
        return ticketSaleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TicketSaleResponse getTicketSaleById(Long id) {
        return toResponse(findTicketSale(id));
    }

    @Transactional(readOnly = true)
    public SaleDetailsDTO getTicketSaleDetails(Long id) {
        TicketSale ticketSale = ticketSaleRepository.findByIdWithSalePromotions(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));

        List<SaleDetailsDTO.AppliedPromotionDTO> appliedPromotions = ticketSale.getSalePromotions().stream()
                .sorted((first, second) -> first.getAppliedAt().compareTo(second.getAppliedAt()))
                .map(this::toAppliedPromotion)
                .toList();

        double totalDiscount = appliedPromotions.stream()
                .mapToDouble(promotion -> promotion.getDiscountApplied() == null ? 0.0 : promotion.getDiscountApplied())
                .sum();
        double originalAmount = ticketSale.getAmount() == null ? 0.0 : ticketSale.getAmount();

        SaleDetailsDTO response = new SaleDetailsDTO();
        response.setSaleId(ticketSale.getId());
        response.setBookingId(ticketSale.getBookingId());
        response.setUserId(ticketSale.getUserId());
        response.setOriginalAmount(originalAmount);
        response.setMethod(ticketSale.getMethod());
        response.setStatus(ticketSale.getStatus());
        response.setTransactionDetails(copyTransactionDetails(ticketSale.getTransactionDetails()));
        response.setAppliedPromotions(appliedPromotions);
        response.setTotalDiscount(totalDiscount);
        response.setFinalAmount(originalAmount - totalDiscount);
        return response;
    }

    @Transactional
    public TicketSaleResponse createTicketSale(TicketSaleRequest request) {
        TicketSale ticketSale = new TicketSale();
        apply(ticketSale, request);
        return toResponse(ticketSaleRepository.save(ticketSale));
    }

    @Transactional
    public TicketSaleResponse updateTicketSale(Long id, TicketSaleRequest request) {
        TicketSale existing = findTicketSale(id);
        apply(existing, request);
        return toResponse(ticketSaleRepository.save(existing));
    }

    @Transactional
    public void deleteTicketSale(Long id) {
        ticketSaleRepository.delete(findTicketSale(id));
    }

    @Transactional
    public TicketSaleResponse processBookingSale(Long bookingId, ProcessBookingSaleRequest request) {
        if (request == null || request.getMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is required");
        }

        BookingJdbcRepository.BookingPaymentRow booking = bookingJdbcRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!"COMPLETED".equals(booking.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking must be completed before payment");
        }

        if (ticketSaleRepository.existsByBookingIdAndStatus(bookingId, TicketSaleStatus.COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
        }

        TicketSale ticketSale = findPendingTicketSaleForBooking(bookingId);
        ticketSale.setStatus(TicketSaleStatus.COMPLETED);
        ticketSale.setMethod(request.getMethod());
        if (booking.totalAmount() != null) {
            ticketSale.setAmount(booking.totalAmount());
        }
        ticketSale.setTransactionDetails(buildProcessedTransactionDetails(ticketSale, request));

        return toResponse(ticketSaleRepository.save(ticketSale));
    }

    @Transactional
    public TicketSale retryFailedSale(Long id) {
        TicketSale sale = findTicketSale(id);
        if (sale.getStatus() != TicketSaleStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale is not in FAILED status");
        }

        Map<String, Object> details = new LinkedHashMap<>(copyTransactionDetails(sale.getTransactionDetails()));
        int retryAttempt = 0;
        Object rawRetry = details.get("retryAttempt");
        if (rawRetry instanceof Number) {
            retryAttempt = ((Number) rawRetry).intValue();
        }

        details.put("retryAttempt", retryAttempt + 1);
        details.put("gatewayResponse", "approved");
        sale.setTransactionDetails(details);
        sale.setStatus(TicketSaleStatus.COMPLETED);

        return ticketSaleRepository.save(sale);
    }

    @Transactional
    public TicketSale refundTicketSale(Long id, RefundRequest request) {
        TicketSale sale = findTicketSale(id);
        if (sale.getStatus() != TicketSaleStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale must be completed before refund");
        }

        Map<String, Object> details = new LinkedHashMap<>(copyTransactionDetails(sale.getTransactionDetails()));
        details.put("refundReason", request == null ? null : request.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());

        sale.setTransactionDetails(details);
        sale.setStatus(TicketSaleStatus.REFUNDED);
        return ticketSaleRepository.save(sale);
    }

    private TicketSale findTicketSale(Long id) {
        return ticketSaleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found"));
    }

    private void apply(TicketSale ticketSale, TicketSaleRequest request) {
        ticketSale.setBookingId(request.getBookingId());
        ticketSale.setUserId(request.getUserId());
        ticketSale.setAmount(request.getAmount());
        ticketSale.setMethod(request.getMethod());
        ticketSale.setStatus(request.getStatus());
        ticketSale.setTransactionDetails(request.getTransactionDetails());
    }

    private TicketSale findPendingTicketSaleForBooking(Long bookingId) {
        List<TicketSale> ticketSales = ticketSaleRepository.findByBookingIdForUpdate(bookingId);
        if (ticketSales.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket sale not found for booking");
        }
        if (ticketSales.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, ticketSales.size());
        }

        TicketSale ticketSale = ticketSales.get(0);
        if (ticketSale.getStatus() != TicketSaleStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket sale must be pending for payment");
        }
        return ticketSale;
    }

    private TicketSaleResponse toResponse(TicketSale ticketSale) {
        TicketSaleResponse response = new TicketSaleResponse();
        response.setId(ticketSale.getId());
        response.setBookingId(ticketSale.getBookingId());
        response.setUserId(ticketSale.getUserId());
        response.setAmount(ticketSale.getAmount());
        response.setMethod(ticketSale.getMethod());
        response.setStatus(ticketSale.getStatus());
        response.setTransactionDetails(ticketSale.getTransactionDetails());
        response.setCreatedAt(ticketSale.getCreatedAt());
        return response;
    }

    private SaleDetailsDTO.AppliedPromotionDTO toAppliedPromotion(SalePromotion salePromotion) {
        SaleDetailsDTO.AppliedPromotionDTO response = new SaleDetailsDTO.AppliedPromotionDTO();
        response.setPromotionCode(salePromotion.getPromotion().getCode());
        response.setDiscountType(salePromotion.getPromotion().getDiscountType());
        response.setDiscountApplied(salePromotion.getDiscountApplied());
        response.setAppliedAt(salePromotion.getAppliedAt());
        return response;
    }

    private Map<String, Object> copyTransactionDetails(Map<String, Object> transactionDetails) {
        return transactionDetails == null ? new LinkedHashMap<>() : new LinkedHashMap<>(transactionDetails);
    }

    private Map<String, Object> buildProcessedTransactionDetails(
            TicketSale ticketSale,
            ProcessBookingSaleRequest request
    ) {
        Map<String, Object> transactionDetails = new LinkedHashMap<>(copyTransactionDetails(ticketSale.getTransactionDetails()));
        transactionDetails.put("paymentMethod", request.getMethod().name());
        transactionDetails.put("paidAt", LocalDateTime.now().toString());

        if (request.getCardLastFour() != null && !request.getCardLastFour().isBlank()) {
            transactionDetails.put("cardLastFour", request.getCardLastFour().trim());
        }

        return transactionDetails;
    }
    @Transactional(readOnly = true)
    public RevenueReportDTO getRevenueReport(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<TicketSale> sales = ticketSaleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                startDateTime,
                endDateTime
        );

        double totalRevenue = sales.stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.COMPLETED)
                .mapToDouble(sale -> sale.getAmount() == null ? 0.0 : sale.getAmount())
                .sum();

        long totalTransactions = sales.stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.COMPLETED)
                .count();

        double averageSale = totalTransactions == 0 ? 0.0 : totalRevenue / totalTransactions;

        double refundedAmount = sales.stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.REFUNDED)
                .mapToDouble(sale -> sale.getAmount() == null ? 0.0 : sale.getAmount())
                .sum();

        long refundCount = sales.stream()
                .filter(sale -> sale.getStatus() == TicketSaleStatus.REFUNDED)
                .count();

        return new RevenueReportDTO(
                totalRevenue,
                totalTransactions,
                averageSale,
                refundedAmount,
                refundCount
        );
    }

    @Transactional(readOnly = true)
    public UserSaleSummaryDTO getUserSaleSummary(Long userId) {
        if (!userJdbcRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        List<TicketSaleRepository.PaymentMethodSummaryProjection> rows =
                ticketSaleRepository.getCompletedSalesSummaryByMethod(userId);

        Map<String, Double> methodBreakdown = new LinkedHashMap<>();
        long totalSales = 0L;
        double totalAmount = 0.0;

        for (TicketSaleRepository.PaymentMethodSummaryProjection row : rows) {
            String method = row.getMethod();
            Long saleCount = row.getSaleCount() == null ? 0L : row.getSaleCount();
            Double amount = row.getTotalAmount() == null ? 0.0 : row.getTotalAmount();

            methodBreakdown.put(method, amount);
            totalSales += saleCount;
            totalAmount += amount;
        }

        return new UserSaleSummaryDTO(userId, totalSales, totalAmount, methodBreakdown);
    }
}
