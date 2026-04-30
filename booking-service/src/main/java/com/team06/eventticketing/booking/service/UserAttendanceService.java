package com.team06.eventticketing.booking.service;

import com.team06.eventticketing.booking.dto.AttendanceResponse;
import com.team06.eventticketing.booking.dto.BookingAttendanceData;
import com.team06.eventticketing.booking.repository.AttendanceGraphRepository;
import com.team06.eventticketing.booking.repository.BookingAttendanceLookupRepository;
import com.team06.eventticketing.common.cache.RedisCacheService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAttendanceService {

    private final BookingAttendanceLookupRepository lookupRepository;
    private final AttendanceGraphRepository attendanceGraphRepository;
    private final BookingService bookingService;
    private final RedisCacheService redisCacheService;

    public UserAttendanceService(
            BookingAttendanceLookupRepository lookupRepository,
            AttendanceGraphRepository attendanceGraphRepository,
            BookingService bookingService,
            RedisCacheService redisCacheService) {
        this.lookupRepository = lookupRepository;
        this.attendanceGraphRepository = attendanceGraphRepository;
        this.bookingService = bookingService;
        this.redisCacheService = redisCacheService;
    }

    @Transactional
    public AttendanceResponse recordAttendance(Long bookingId) {
        BookingAttendanceData data = lookupRepository.findAttendanceDataByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!"COMPLETED".equalsIgnoreCase(data.bookingStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Attendance can only be recorded for COMPLETED bookings");
        }
        if (data.eventId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking has no assigned event");
        }
        if (data.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking has no assigned user");
        }

        if (attendanceGraphRepository.alreadyRecorded(data.userId(), data.eventId(), data.bookingId())) {
            return new AttendanceResponse(
                    "Attendance was already recorded for this booking",
                    data.bookingId(),
                    data.userId(),
                    data.eventId(),
                    true);
        }

        attendanceGraphRepository.recordAttendance(
                data.userId(),
                data.userName(),
                data.eventId(),
                data.eventName(),
                data.eventCategory(),
                data.bookingId());

        Map<String, Object> details = new HashMap<>();
        details.put("bookingId", data.bookingId());
        details.put("userId", data.userId());
        details.put("eventId", data.eventId());
        bookingService.notifyObservers("INTERACTION_RECORDED", details);
        redisCacheService.deleteByPattern("booking-service::S3-F12::*");

        return new AttendanceResponse(
                "Attendance recorded successfully",
                data.bookingId(),
                data.userId(),
                data.eventId(),
                false);
    }
}
