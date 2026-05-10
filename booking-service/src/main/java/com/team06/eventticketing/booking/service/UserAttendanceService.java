package com.team06.eventticketing.booking.service;

import com.team06.eventticketing.booking.dto.AttendanceResponse;
import com.team06.eventticketing.booking.model.Booking;
import com.team06.eventticketing.booking.model.BookingStatus;
import com.team06.eventticketing.booking.repository.AttendanceGraphRepository;
import com.team06.eventticketing.booking.repository.BookingRepository;
import com.team06.eventticketing.common.cache.RedisCacheService;
import com.team06.eventticketing.contracts.dto.EventDTO;
import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.feign.EventServiceClient;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import feign.FeignException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAttendanceService {

    private final BookingRepository bookingRepository;
    private final AttendanceGraphRepository attendanceGraphRepository;
    private final BookingService bookingService;
    private final RedisCacheService redisCacheService;
    private final UserServiceClient userServiceClient;
    private final EventServiceClient eventServiceClient;

    public UserAttendanceService(
            BookingRepository bookingRepository,
            AttendanceGraphRepository attendanceGraphRepository,
            BookingService bookingService,
            RedisCacheService redisCacheService,
            UserServiceClient userServiceClient,
            EventServiceClient eventServiceClient) {
        this.bookingRepository = bookingRepository;
        this.attendanceGraphRepository = attendanceGraphRepository;
        this.bookingService = bookingService;
        this.redisCacheService = redisCacheService;
        this.userServiceClient = userServiceClient;
        this.eventServiceClient = eventServiceClient;
    }

    @Transactional
    public AttendanceResponse recordAttendance(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != BookingStatus.COMPLETED && booking.getStatus() != BookingStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Attendance can only be recorded for completed bookings");
        }
        if (booking.getEventId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking has no assigned event");
        }
        if (booking.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking has no assigned user");
        }

        UserDTO user = getUser(booking.getUserId());
        EventDTO event = getEvent(booking.getEventId());

        if (attendanceGraphRepository.alreadyRecorded(booking.getUserId(), booking.getEventId(), booking.getId())) {
            return new AttendanceResponse(
                    "Attendance was already recorded for this booking",
                    booking.getId(),
                    booking.getUserId(),
                    booking.getEventId(),
                    true);
        }

        attendanceGraphRepository.recordAttendance(
                booking.getUserId(),
                displayName(user),
                booking.getEventId(),
                event.name(),
                event.category(),
                booking.getId());

        Map<String, Object> details = new HashMap<>();
        details.put("bookingId", booking.getId());
        details.put("userId", booking.getUserId());
        details.put("eventId", booking.getEventId());
        bookingService.notifyObservers("INTERACTION_RECORDED", details);
        redisCacheService.deleteByPattern("booking-service::S3-F12::*");

        return new AttendanceResponse(
                "Attendance recorded successfully",
                booking.getId(),
                booking.getUserId(),
                booking.getEventId(),
                false);
    }

    private UserDTO getUser(Long userId) {
        try {
            return userServiceClient.getUser(userId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable", exception);
        }
    }

    private EventDTO getEvent(Long eventId) {
        try {
            return eventServiceClient.getEvent(eventId);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found", exception);
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event service temporarily unavailable", exception);
        }
    }

    private String displayName(UserDTO user) {
        if (user.name() != null && !user.name().isBlank()) {
            return user.name();
        }
        return user.email();
    }
}
