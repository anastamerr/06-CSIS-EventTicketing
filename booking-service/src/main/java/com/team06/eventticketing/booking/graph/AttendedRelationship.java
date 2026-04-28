package com.team06.eventticketing.booking.graph;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AttendedRelationship {

    private Integer attendanceCount = 0;
    private LocalDateTime lastAttendedDate;
    private List<Long> recordedBookingIds = new ArrayList<>();

    public Integer getAttendanceCount() {
        return attendanceCount;
    }

    public void setAttendanceCount(Integer attendanceCount) {
        this.attendanceCount = attendanceCount;
    }

    public LocalDateTime getLastAttendedDate() {
        return lastAttendedDate;
    }

    public void setLastAttendedDate(LocalDateTime lastAttendedDate) {
        this.lastAttendedDate = lastAttendedDate;
    }

    public List<Long> getRecordedBookingIds() {
        return recordedBookingIds;
    }

    public void setRecordedBookingIds(List<Long> recordedBookingIds) {
        this.recordedBookingIds = recordedBookingIds == null ? new ArrayList<>() : recordedBookingIds;
    }
}
