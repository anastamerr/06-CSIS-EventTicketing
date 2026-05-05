package com.team06.eventticketing.ticket.scan;

import com.fasterxml.jackson.annotation.JsonAlias;

public class TicketScanRequest {

    @JsonAlias("scan_type")
    private String scanType;
    private String gate;
    private String section;
    @JsonAlias("seat_number")
    private String seatNumber;
    private String notes;

    public String getScanType() {
        return scanType;
    }

    public void setScanType(String scanType) {
        this.scanType = scanType;
    }

    public String getGate() {
        return gate;
    }

    public void setGate(String gate) {
        this.gate = gate;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
