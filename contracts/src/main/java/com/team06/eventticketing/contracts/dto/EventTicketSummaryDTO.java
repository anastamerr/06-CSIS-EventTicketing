package com.team06.eventticketing.contracts.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class EventTicketSummaryDTO {

    @JsonAlias("totalTickets")
    private long totalTicketsSold;

    @JsonAlias("usedTickets")
    private long usedCount;

    public EventTicketSummaryDTO() {
    }

    public EventTicketSummaryDTO(long totalTicketsSold, long usedCount) {
        this.totalTicketsSold = totalTicketsSold;
        this.usedCount = usedCount;
    }

    public long totalTicketsSold() {
        return totalTicketsSold;
    }

    public long usedCount() {
        return usedCount;
    }

    public long getTotalTicketsSold() {
        return totalTicketsSold;
    }

    public void setTotalTicketsSold(long totalTicketsSold) {
        this.totalTicketsSold = totalTicketsSold;
    }

    public long getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(long usedCount) {
        this.usedCount = usedCount;
    }
}
