package com.team06.eventticketing.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AvgCapacityDTO {

    private Double avgCapacity;

    public Double getAvgCapacity() {
        return avgCapacity;
    }

    public void setAvgCapacity(Double avgCapacity) {
        this.avgCapacity = avgCapacity;
    }
}