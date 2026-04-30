package com.team06.eventticketing.ticket.repository;

import com.team06.eventticketing.ticket.model.TicketAnalyticsEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TicketAnalyticsEventRepository extends MongoRepository<TicketAnalyticsEvent, String> {
}
