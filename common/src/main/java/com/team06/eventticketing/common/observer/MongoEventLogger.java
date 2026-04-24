package com.team06.eventticketing.common.observer;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final MongoTemplate mongoTemplate;
    private final EventFactory eventFactory;
    private final EventType boundEventType;
    private final String collectionName;

    public MongoEventLogger(
            MongoTemplate mongoTemplate,
            EventFactory eventFactory,
            EventType boundEventType,
            String collectionName
    ) {
        this.mongoTemplate = mongoTemplate;
        this.eventFactory = eventFactory;
        this.boundEventType = boundEventType;
        this.collectionName = collectionName;
    }

    @Override
    public void onEvent(String action, Object payload) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("action", action);
            params.put("timestamp", LocalDateTime.now());
            if (payload instanceof Map<?, ?> source) {
                for (Map.Entry<?, ?> entry : source.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        params.put(key, entry.getValue());
                    }
                }
            }
            MongoEvent event = eventFactory.createEvent(boundEventType, params);
            mongoTemplate.save(event, collectionName);
        } catch (Exception exception) {
            log.warn("Mongo event logging failed for action {}", action, exception);
        }
    }
}
