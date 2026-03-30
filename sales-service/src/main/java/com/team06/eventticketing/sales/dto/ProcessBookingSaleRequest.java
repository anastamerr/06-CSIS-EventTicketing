package com.team06.eventticketing.sales.dto;

import com.team06.eventticketing.sales.model.TicketSaleMethod;

public class ProcessBookingSaleRequest {

    private TicketSaleMethod method;
    private String cardLastFour;

    public TicketSaleMethod getMethod() {
        return method;
    }

    public void setMethod(TicketSaleMethod method) {
        this.method = method;
    }

    public String getCardLastFour() {
        return cardLastFour;
    }

    public void setCardLastFour(String cardLastFour) {
        this.cardLastFour = cardLastFour;
    }
}
