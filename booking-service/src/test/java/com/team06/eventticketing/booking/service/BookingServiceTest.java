package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team06.eventticketing.booking.dto.BookingAnalyticsDTO;
import com.team06.eventticketing.booking.dto.BookingAnalyticsDashboardDTO;
import com.team06.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team06.eventticketing.booking.dto.BookingEstimateRequest;
import com.team06.eventticketing.booking.dto.BookingItemRequest;
import com.team06.eventticketing.booking.dto.BookingRequest;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingItem;
import com.team06.eventticketing.booking.model.BookingItemStatus;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.booking.repository.TicketJdbcRepository;
import com.team06.eventticketing.booking.repository.TicketSaleJdbcRepository;
import com.team06.eventticketing.common.cache.CacheAspect;
import com.team06.eventticketing.common.cache.CachedFeature;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.common.observer.EntityObserver;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketSaleJdbcRepository ticketSaleJdbcRepository;

    @Mock
    private TicketJdbcRepository ticketJdbcRepository;

    @Captor
    private ArgumentCaptor<Map<String, Object>> transactionDetailsCaptor;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, ticketJdbcRepository, ticketSaleJdbcRepository);
    }

    @Test
    void createBookingDefaultsStatusWhenMissing() {
        BookingRequest request = new BookingRequest();
        request.setUserId(1L);
        request.setContactEmail("buyer@example.com");

        Booking persisted = new Booking();
        persisted.setId(1L);
        persisted.setUserId(1L);
        persisted.setContactEmail("buyer@example.com");
        persisted.setStatus(BookingStatus.PENDING);

        when(bookingRepository.save(org.mockito.ArgumentMatchers.any(Booking.class))).thenReturn(persisted);

        Booking result = bookingService.createBooking(request);

        assertEquals(BookingStatus.PENDING, result.getStatus());
        verify(bookingRepository).save(org.mockito.ArgumentMatchers.any(Booking.class));
    }

    @Test
    void estimateBookingCostCalculatesExpectedValuesForVipTier() {
        BookingEstimateRequest request = new BookingEstimateRequest(77L, 2, "VIP");

        when(bookingRepository.findAverageSessionCapacityByEventId(77L)).thenReturn(500.0);
        when(bookingRepository.countActiveBookingsByEventId(77L)).thenReturn(0L);

        BookingCostEstimateDTO result = bookingService.estimateBookingCost(request);

        assertEquals(250.0, result.ticketCost());
        assertEquals(37.5, result.serviceFee());
        assertEquals(1.0, result.demandMultiplier());
        assertEquals(287.5, result.estimatedTotal());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void estimateBookingCostUsesDemandMultiplierTiers() {
        BookingEstimateRequest request = new BookingEstimateRequest(77L, 1, "standard");

        when(bookingRepository.findAverageSessionCapacityByEventId(77L)).thenReturn(1000.0);
        when(bookingRepository.countActiveBookingsByEventId(77L)).thenReturn(60L);

        BookingCostEstimateDTO result = bookingService.estimateBookingCost(request);

        assertEquals(100.0, result.ticketCost());
        assertEquals(15.0, result.serviceFee());
        assertEquals(1.25, result.demandMultiplier());
        assertEquals(143.75, result.estimatedTotal());
    }

    @Test
    void estimateBookingCostRejectsMissingSessions() {
        BookingEstimateRequest request = new BookingEstimateRequest(77L, 1, "standard");
        when(bookingRepository.findAverageSessionCapacityByEventId(77L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.estimateBookingCost(request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updateBookingRejectsMissingBooking() {
        when(bookingRepository.findByIdWithBookingItems(7L)).thenReturn(Optional.empty());

        BookingRequest request = new BookingRequest();
        request.setContactEmail("updated@example.com");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.updateBooking(7L, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void completeBookingCalculatesTotalCreatesSaleAndCompletesBooking() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setUserId(11L);
        booking.setEventId(22L);
        booking.setContactEmail("buyer@example.com");
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setMetadata(new LinkedHashMap<>(Map.of("paymentMethod", "credit_card")));
        booking.addBookingItem(bookingItem(1, 2, 100.0, BookingItemStatus.RESERVED));
        booking.addBookingItem(bookingItem(2, 1, 250.0, BookingItemStatus.RESERVED));
        booking.addBookingItem(bookingItem(3, 4, 50.0, BookingItemStatus.RESERVED));

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));
        when(ticketSaleJdbcRepository.existsByBookingId(5L)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(5L);

        assertEquals(BookingStatus.COMPLETED, result.getStatus());
        assertEquals(650.0, result.getTotalAmount());

        verify(ticketSaleJdbcRepository).createPendingSale(
                eq(5L),
                eq(11L),
                eq(650.0),
                eq("CREDIT_CARD"),
                transactionDetailsCaptor.capture()
        );

        Map<String, Object> transactionDetails = transactionDetailsCaptor.getValue();
        assertEquals(650.0, transactionDetails.get("bookingTotalAmount"));
        verify(bookingRepository).save(booking);
    }

    @Test
    void completeBookingRejectsPendingBooking() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(5L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleJdbcRepository, never()).existsByBookingId(any());
        verify(ticketSaleJdbcRepository, never()).createPendingSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void completeBookingRejectsConfirmedBooking() {
        Booking booking = new Booking();
        booking.setId(6L);
        booking.setUserId(12L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setMetadata(new LinkedHashMap<>());
        booking.addBookingItem(bookingItem(1, 2, 75.0, BookingItemStatus.RESERVED));

        when(bookingRepository.findByIdWithBookingItemsForUpdate(6L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(6L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleJdbcRepository, never()).existsByBookingId(any());
        verify(ticketSaleJdbcRepository, never()).createPendingSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void completeBookingDoesNotCreateDuplicateTicketSale() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setUserId(11L);
        booking.setEventId(22L);
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.addBookingItem(bookingItem(1, 1, 100.0, BookingItemStatus.RESERVED));

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));
        when(ticketSaleJdbcRepository.existsByBookingId(5L)).thenReturn(true);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.completeBooking(5L);

        assertEquals(BookingStatus.COMPLETED, result.getStatus());
        assertEquals(100.0, result.getTotalAmount());
        verify(ticketSaleJdbcRepository, never()).createPendingSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository).save(booking);
    }

    @Test
    void completeBookingRejectsAlreadyCompletedBooking() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setUserId(11L);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setTotalAmount(90.0);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(5L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.completeBooking(5L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketSaleJdbcRepository, never()).existsByBookingId(any());
        verify(ticketSaleJdbcRepository, never()).createPendingSale(any(), any(), anyDouble(), anyString(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getAllBookingsUsesFetchJoinRepository() {
        when(bookingRepository.findAllWithBookingItems()).thenReturn(List.of());

        bookingService.getAllBookings();

        verify(bookingRepository).findAllWithBookingItems();
    }

    @Test
    void searchBookingsFiltersByStatusAndDateRange() {
        Booking marchCompletedBooking = new Booking();
        marchCompletedBooking.setId(8L);
        Booking earlierMarchCompletedBooking = new Booking();
        earlierMarchCompletedBooking.setId(6L);
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        when(bookingRepository.findByStatusAndBookingDateBetweenOrderByBookingDateDesc(
                BookingStatus.COMPLETED,
                startDateTime,
                endDateTime
        )).thenReturn(List.of(marchCompletedBooking, earlierMarchCompletedBooking));

        List<Booking> result = bookingService.searchBookings(BookingStatus.COMPLETED, startDate, endDate);

        assertEquals(List.of(marchCompletedBooking, earlierMarchCompletedBooking), result);
        verify(bookingRepository).findByStatusAndBookingDateBetweenOrderByBookingDateDesc(
                BookingStatus.COMPLETED,
                startDateTime,
                endDateTime
        );
    }

    @Test
    void searchBookingsUsesDateRangeOnlyWhenStatusMissing() {
        Booking mostRecentMarchBooking = new Booking();
        mostRecentMarchBooking.setId(9L);
        Booking secondMarchBooking = new Booking();
        secondMarchBooking.setId(8L);
        Booking thirdMarchBooking = new Booking();
        thirdMarchBooking.setId(7L);
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        when(bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(startDateTime, endDateTime))
                .thenReturn(List.of(mostRecentMarchBooking, secondMarchBooking, thirdMarchBooking));

        List<Booking> result = bookingService.searchBookings(null, startDate, endDate);

        assertEquals(List.of(mostRecentMarchBooking, secondMarchBooking, thirdMarchBooking), result);
        verify(bookingRepository).findByBookingDateBetweenOrderByBookingDateDesc(startDateTime, endDateTime);
    }

    @Test
    void searchBookingsRejectsMissingDates() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.searchBookings(BookingStatus.COMPLETED, null, LocalDate.of(2026, 3, 31)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void searchBookingsRejectsStartDateAfterEndDate() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.searchBookings(
                        BookingStatus.COMPLETED,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 3, 31)
                ));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getBookingAnalyticsMapsAggregateRowToDto() {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        when(bookingRepository.findAnalyticsByDateRange(startDateTime, endDateTime))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 7L, 3L, 3500.0, 500.0}));

        BookingAnalyticsDTO result = bookingService.getBookingAnalytics(startDate, endDate);

        assertEquals(10L, result.totalBookings());
        assertEquals(7L, result.completedBookings());
        assertEquals(3L, result.cancelledBookings());
        assertEquals(3500.0, result.totalRevenue());
        assertEquals(500.0, result.averageBookingAmount());
        assertEquals(70.0, result.completionRate());
        verify(bookingRepository).findAnalyticsByDateRange(startDateTime, endDateTime);
    }

    @Test
    void getBookingAnalyticsDefaultsNullAggregateValuesToZero() {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);

        when(bookingRepository.findAnalyticsByDateRange(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX)))
                .thenReturn(List.<Object[]>of(new Object[]{0L, null, null, null, null}));

        BookingAnalyticsDTO result = bookingService.getBookingAnalytics(startDate, endDate);

        assertEquals(0L, result.totalBookings());
        assertEquals(0L, result.completedBookings());
        assertEquals(0L, result.cancelledBookings());
        assertEquals(0.0, result.totalRevenue());
        assertEquals(0.0, result.averageBookingAmount());
        assertEquals(0.0, result.completionRate());
    }

    @Test
    void getBookingAnalyticsDashboardMapsMarchAggregateRowsToDto() {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        when(bookingRepository.findDashboardAnalytics(startDateTime, endDateTime))
                .thenReturn(List.<Object[]>of(
                        new Object[]{6L, 1500.0, 2L, 4L, "PENDING", 1L},
                        new Object[]{6L, 1500.0, 2L, 4L, "CONFIRMED", 1L},
                        new Object[]{6L, 1500.0, 2L, 4L, "CHECKED_IN", 1L},
                        new Object[]{6L, 1500.0, 2L, 4L, "COMPLETED", 2L},
                        new Object[]{6L, 1500.0, 2L, 4L, "CANCELLED", 1L}));

        BookingAnalyticsDashboardDTO result = bookingService.getBookingAnalyticsDashboard(startDate, endDate);

        assertEquals(6L, result.totalBookings());
        assertEquals(1500.0, result.totalRevenue());
        assertEquals(750.0, result.averageBookingValue());
        assertEquals(4.0 / 6.0, result.conversionRate());
        assertEquals(1L, result.bookingsByStatus().get("PENDING"));
        assertEquals(1L, result.bookingsByStatus().get("CONFIRMED"));
        assertEquals(1L, result.bookingsByStatus().get("CHECKED_IN"));
        assertEquals(2L, result.bookingsByStatus().get("COMPLETED"));
        assertEquals(1L, result.bookingsByStatus().get("CANCELLED"));
        verify(bookingRepository).findDashboardAnalytics(startDateTime, endDateTime);
    }

    @Test
    void getBookingAnalyticsDashboardReturnsZeroBreakdownForEmptyDateRange() {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        when(bookingRepository.findDashboardAnalytics(startDateTime, endDateTime))
                .thenReturn(List.<Object[]>of(new Object[]{0L, 0.0, 0L, 0L, null, 0L}));

        BookingAnalyticsDashboardDTO result = bookingService.getBookingAnalyticsDashboard(startDate, endDate);

        assertEquals(0L, result.totalBookings());
        assertEquals(0.0, result.totalRevenue());
        assertEquals(0.0, result.averageBookingValue());
        assertEquals(0.0, result.conversionRate());
        for (BookingStatus status : BookingStatus.values()) {
            assertEquals(0L, result.bookingsByStatus().get(status.name()));
        }
    }

    @Test
    void getBookingAnalyticsDashboardRejectsInvalidDateRangeBeforeLogging() {
        EntityObserver observer = mock(EntityObserver.class);
        bookingService.register(observer);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.getBookingAnalyticsDashboard(
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 3, 31)
                ));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(observer);
        verify(bookingRepository, never()).findDashboardAnalytics(any(), any());
    }

    @Test
    void getBookingAnalyticsDashboardLogsOutsideCachedAggregation() {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        BookingService serviceSpy = spy(bookingService);
        EntityObserver observer = mock(EntityObserver.class);
        BookingAnalyticsDashboardDTO dashboard = BookingAnalyticsDashboardDTO.builder()
                .totalBookings(1L)
                .totalRevenue(300.0)
                .averageBookingValue(300.0)
                .conversionRate(1.0)
                .bookingsByStatus(Map.of("COMPLETED", 1L))
                .build();

        serviceSpy.register(observer);
        doReturn(dashboard).when(serviceSpy).getCachedBookingAnalyticsDashboard(startDateTime, endDateTime);

        serviceSpy.getBookingAnalyticsDashboard(startDate, endDate);
        serviceSpy.getBookingAnalyticsDashboard(startDate, endDate);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(observer, times(2)).onEvent(eq("ANALYTICS_VIEWED"), payloadCaptor.capture());
        verify(serviceSpy, times(2)).getCachedBookingAnalyticsDashboard(startDateTime, endDateTime);

        Map<?, ?> payload = (Map<?, ?>) payloadCaptor.getAllValues().get(0);
        Map<?, ?> details = (Map<?, ?>) payload.get("details");
        assertEquals("2026-03-01", details.get("startDate"));
        assertEquals("2026-03-31", details.get("endDate"));
    }

    @Test
    void dashboardCacheHitStillLogsAnalyticsViewWithoutReaggregating() {
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        AtomicReference<BookingService> proxyReference = new AtomicReference<>();
        ObjectProvider<BookingService> selfProvider = new ObjectProvider<>() {
            @Override
            public BookingService getObject() {
                return proxyReference.get();
            }
        };
        BookingService target = new BookingService(
                bookingRepository,
                ticketJdbcRepository,
                ticketSaleJdbcRepository,
                new com.team06.eventticketing.booking.adapter.BookingAnalyticsAdapter(),
                null,
                null,
                selfProvider);
        RedisCacheService redisCacheService = mock(RedisCacheService.class);
        BookingAnalyticsDashboardDTO cachedDashboard = BookingAnalyticsDashboardDTO.builder()
                .totalBookings(3L)
                .totalRevenue(300.0)
                .averageBookingValue(300.0)
                .conversionRate(2.0 / 3.0)
                .bookingsByStatus(Map.of("COMPLETED", 1L))
                .build();
        EntityObserver observer = mock(EntityObserver.class);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new CacheAspect(redisCacheService, new ObjectMapper()));
        BookingService proxy = proxyFactory.getProxy();
        proxyReference.set(proxy);

        when(redisCacheService.stableHash(any())).thenReturn("dashboard-key");
        when(redisCacheService.get(eq("booking-service::S3-F10::dashboard-key"), any(JavaType.class)))
                .thenReturn(null)
                .thenReturn(cachedDashboard);
        when(bookingRepository.findDashboardAnalytics(startDateTime, endDateTime))
                .thenReturn(List.<Object[]>of(
                        new Object[]{3L, 300.0, 1L, 2L, "PENDING", 1L},
                        new Object[]{3L, 300.0, 1L, 2L, "CONFIRMED", 1L},
                        new Object[]{3L, 300.0, 1L, 2L, "COMPLETED", 1L}));

        proxy.register(observer);
        proxy.getBookingAnalyticsDashboard(startDate, endDate);
        proxy.getBookingAnalyticsDashboard(startDate, endDate);

        verify(observer, times(2)).onEvent(eq("ANALYTICS_VIEWED"), any());
        verify(bookingRepository, times(1)).findDashboardAnalytics(startDateTime, endDateTime);
        verify(redisCacheService).put(
                eq("booking-service::S3-F10::dashboard-key"),
                any(BookingAnalyticsDashboardDTO.class),
                eq(600L));
    }

    @Test
    void cachedDashboardAggregationMethodCarriesFeatureCacheMetadata() throws NoSuchMethodException {
        CachedFeature cachedFeature = BookingService.class
                .getMethod("getCachedBookingAnalyticsDashboard", LocalDateTime.class, LocalDateTime.class)
                .getAnnotation(CachedFeature.class);

        assertNotNull(cachedFeature);
        assertEquals("booking-service", cachedFeature.service());
        assertEquals("S3-F10", cachedFeature.featureId());
        assertEquals(600L, cachedFeature.ttlSeconds());
    }

    @Test
    void bookingAnalyticsDashboardDtoSerializesAndDeserializesExpectedShape() throws Exception {
        BookingAnalyticsDashboardDTO dashboard = BookingAnalyticsDashboardDTO.builder()
                .totalBookings(3L)
                .totalRevenue(300.0)
                .averageBookingValue(300.0)
                .conversionRate(2.0 / 3.0)
                .bookingsByStatus(Map.of(
                        "PENDING", 1L,
                        "CONFIRMED", 1L,
                        "COMPLETED", 1L))
                .build();
        ObjectMapper objectMapper = new ObjectMapper();

        String payload = objectMapper.writeValueAsString(dashboard);
        JsonNode json = objectMapper.readTree(payload);
        BookingAnalyticsDashboardDTO roundTrip = objectMapper.readValue(payload, BookingAnalyticsDashboardDTO.class);

        assertEquals(5, json.size());
        assertEquals(3L, json.get("totalBookings").asLong());
        assertEquals(300.0, json.get("totalRevenue").asDouble());
        assertEquals(300.0, json.get("averageBookingValue").asDouble());
        assertEquals(2.0 / 3.0, json.get("conversionRate").asDouble());
        assertEquals(1L, json.get("bookingsByStatus").get("COMPLETED").asLong());
        assertEquals(3L, roundTrip.totalBookings());
        assertEquals(1L, roundTrip.bookingsByStatus().get("COMPLETED"));
    }

    @Test
    void bookingAnalyticsDashboardDtoExposesStaticFluentBuilder() throws Exception {
        Method builderMethod = BookingAnalyticsDashboardDTO.class.getDeclaredMethod("builder");
        Object builder = builderMethod.invoke(null);

        assertTrue(Modifier.isStatic(builderMethod.getModifiers()));
        assertEquals("Builder", builder.getClass().getSimpleName());
        assertEquals(builder, builder.getClass()
                .getDeclaredMethod("totalBookings", long.class)
                .invoke(builder, 10L));
        assertEquals(builder, builder.getClass()
                .getDeclaredMethod("totalRevenue", double.class)
                .invoke(builder, 1500.0));
        assertEquals(BookingAnalyticsDashboardDTO.class, builder.getClass()
                .getDeclaredMethod("build")
                .getReturnType());
    }

    @Test
    void cancelBookingCancelsConfirmedBookingAndValidTickets() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.cancelBooking(9L);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(ticketJdbcRepository).cancelValidTicketsForBooking(9L);
        verify(bookingRepository).save(booking);
    }

    @Test
    void cancelBookingRejectsCompletedBooking() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancelBooking(9L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketJdbcRepository, never()).cancelValidTicketsForBooking(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBookingRejectsCheckedInBooking() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.CHECKED_IN);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancelBooking(9L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(ticketJdbcRepository, never()).cancelValidTicketsForBooking(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBookingRejectsMissingBooking() {
        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancelBooking(9L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(ticketJdbcRepository, never()).cancelValidTicketsForBooking(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingAssignsUpcomingEventToPendingBooking() {
        Booking booking = new Booking();
        booking.setId(15L);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(15L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findEventById(22L)).thenReturn(List.<Object[]>of(new Object[]{22L, "UPCOMING"}));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.confirmBooking(15L, 22L);

        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals(22L, result.getEventId());
        assertNotNull(result.getConfirmedAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void confirmBookingRejectsAlreadyConfirmedBooking() {
        Booking booking = new Booking();
        booking.setId(15L);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(15L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.confirmBooking(15L, 22L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingRepository, never()).findEventById(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingRejectsNonUpcomingEvent() {
        Booking booking = new Booking();
        booking.setId(15L);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(15L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findEventById(22L)).thenReturn(List.<Object[]>of(new Object[]{22L, "CANCELLED"}));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.confirmBooking(15L, 22L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingRepository).findEventById(22L);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingRejectsMissingBooking() {
        when(bookingRepository.findByIdWithBookingItemsForUpdate(15L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.confirmBooking(15L, 22L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(bookingRepository, never()).findEventById(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingRejectsMissingEvent() {
        Booking booking = new Booking();
        booking.setId(15L);
        booking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(15L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findEventById(22L)).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.confirmBooking(15L, 22L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(bookingRepository).findEventById(22L);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void addItemsToBookingAssignsSequentialEventOrderAndReservedStatus() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.addBookingItem(bookingItem(1, 1, 100.0, BookingItemStatus.RESERVED));

        BookingItemRequest first = new BookingItemRequest();
        first.setSessionId(11L);
        first.setSessionTitle("Session 11");
        first.setQuantity(2);
        first.setUnitPrice(75.0);

        BookingItemRequest second = new BookingItemRequest();
        second.setSessionId(12L);
        second.setSessionTitle("Session 12");
        second.setQuantity(1);
        second.setUnitPrice(50.0);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.addItemsToBooking(9L, List.of(first, second));

        assertEquals(3, result.getBookingItems().size());
        assertEquals(2, result.getBookingItems().get(1).getEventOrder());
        assertEquals(3, result.getBookingItems().get(2).getEventOrder());
        assertEquals(BookingItemStatus.RESERVED, result.getBookingItems().get(1).getStatus());
        assertEquals(BookingItemStatus.RESERVED, result.getBookingItems().get(2).getStatus());
    }

    @Test
    void addItemsToBookingRejectsCompletedBooking() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.COMPLETED);

        BookingItemRequest request = new BookingItemRequest();
        request.setSessionId(11L);
        request.setSessionTitle("Session 11");
        request.setQuantity(1);
        request.setUnitPrice(75.0);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.addItemsToBooking(9L, List.of(request)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void addItemsToBookingAllowsCheckedInBooking() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.CHECKED_IN);

        BookingItemRequest request = new BookingItemRequest();
        request.setSessionId(11L);
        request.setSessionTitle("Session 11");
        request.setQuantity(1);
        request.setUnitPrice(75.0);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.addItemsToBooking(9L, List.of(request));

        assertEquals(1, result.getBookingItems().size());
        assertEquals(1, result.getBookingItems().get(0).getEventOrder());
        assertEquals(BookingItemStatus.RESERVED, result.getBookingItems().get(0).getStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void addItemsToBookingRejectsMissingSessionTitle() {
        Booking booking = new Booking();
        booking.setId(9L);
        booking.setStatus(BookingStatus.PENDING);

        BookingItemRequest request = new BookingItemRequest();
        request.setSessionId(11L);
        request.setQuantity(1);
        request.setUnitPrice(75.0);

        when(bookingRepository.findByIdWithBookingItemsForUpdate(9L)).thenReturn(Optional.of(booking));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookingService.addItemsToBooking(9L, List.of(request)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }

    private BookingItem bookingItem(int eventOrder, int quantity, double unitPrice, BookingItemStatus status) {
        BookingItem bookingItem = new BookingItem();
        bookingItem.setEventOrder(eventOrder);
        bookingItem.setSessionId((long) eventOrder);
        bookingItem.setSessionTitle("Session " + eventOrder);
        bookingItem.setQuantity(quantity);
        bookingItem.setUnitPrice(unitPrice);
        bookingItem.setStatus(status);
        bookingItem.setMetadata(new LinkedHashMap<>());
        return bookingItem;
    }
}
