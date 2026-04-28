package com.team06.eventticketing.sales.adapter;

import com.team06.eventticketing.common.observer.PaymentAuditEvent;
import com.team06.eventticketing.sales.dto.AuditEventDTO;
import java.util.LinkedHashMap;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentAuditEventAdapter {

    private static final Set<String> PAYMENT_SHAPED_ACTIONS = Set.of(
            "CREATED",
            "COMPLETED",
            "FAILED",
            "REFUNDED",
            "REFUND_DENIED"
    );

    public AuditEventDTO adapt(PaymentAuditEvent event) {
        AuditEventDTO dto = new AuditEventDTO();
        dto.setAction(event.getAction());
        dto.setTimestamp(event.getTimestamp());
        if (PAYMENT_SHAPED_ACTIONS.contains(event.getAction())) {
            dto.setMethod(event.getMethod());
            dto.setAmount(event.getAmount());
        }
        dto.setDetails(event.getDetails() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(event.getDetails()));
        return dto;
    }
}
