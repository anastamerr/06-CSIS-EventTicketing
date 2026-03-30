
package com.team06.eventticketing.booking.dto;

import java.util.List;

public class AddBookingItemsRequest {

    private List<BookingItemRequest> items;

    public List<BookingItemRequest> getItems() {
        return items;
    }

    public void setItems(List<BookingItemRequest> items) {
        this.items = items;
    }
}
