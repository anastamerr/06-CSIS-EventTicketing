package com.team06.eventticketing.booking.controller;

import com.team06.eventticketing.booking.dto.AttendanceResponse;
import com.team06.eventticketing.booking.service.UserAttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class UserAttendanceController {

    private final UserAttendanceService userAttendanceService;

    public UserAttendanceController(UserAttendanceService userAttendanceService) {
        this.userAttendanceService = userAttendanceService;
    }

    @PostMapping({"/{bookingId}/record-attendance", "/{bookingId}/attend"})
    public ResponseEntity<AttendanceResponse> recordAttendance(@PathVariable Long bookingId) {
        return ResponseEntity.ok(userAttendanceService.recordAttendance(bookingId));
    }
}
