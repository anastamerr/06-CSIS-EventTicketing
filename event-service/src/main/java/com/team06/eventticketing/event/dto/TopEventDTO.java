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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long eventId;
        private String name;
        private double rating;
        private long totalBookings;

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder rating(double rating) {
            this.rating = rating;
            return this;
        }

        public Builder totalBookings(long totalBookings) {
            this.totalBookings = totalBookings;
            return this;
        }

        public TopEventDTO build() {
            return new TopEventDTO(eventId, name, rating, totalBookings);
        }
    }
}
