package com.team06.eventticketing.sales.strategy;

import com.team06.eventticketing.sales.model.TicketSale;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class RefundStrategySelector {

    public RefundStrategy select(TicketSale sale, LocalDateTime eventDate) {
        long hoursUntilEvent = Duration.between(LocalDateTime.now(), eventDate).toHours();

        if (hoursUntilEvent > 48) {
            return new FullWindowRefundStrategy(hoursUntilEvent);
        }

        if (hoursUntilEvent > 24) {
            return new PartialWindowRefundStrategy(hoursUntilEvent);
        }

        return new NoRefundStrategy(hoursUntilEvent);
    }
}
