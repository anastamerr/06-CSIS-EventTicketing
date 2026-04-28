package com.team06.eventticketing.event.adapter;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public Map<String, Object> adapt(Document document) {
        return document == null ? Map.of() : new LinkedHashMap<>(document);
    }
}
