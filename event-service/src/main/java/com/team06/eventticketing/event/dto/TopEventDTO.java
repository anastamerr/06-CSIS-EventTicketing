package com.team06.eventticketing.event.dto;

public class TopEventDTO {

    private Long eventId;
    private String name;
    private double rating;
    private long totalBookings;

    public TopEventDTO() {}

    public TopEventDTO(Long eventId, String name, double rating, long totalBookings) {
        this.eventId = eventId;
        this.name = name;
        this.rating = rating;
        this.totalBookings = totalBookings;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public double getRating() {
        return rating;
    }

    public long getTotalBookings() {
        return totalBookings;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public void setTotalBookings(long totalBookings) {
        this.totalBookings = totalBookings;
    }
}