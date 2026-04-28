package com.team06.eventticketing.event.adapter;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchHitAdapter {

    public Map<String, Object> adapt(SearchHit<?> hit) {
        if (hit == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", hit.getId());
        values.put("score", hit.getScore());
        values.put("content", hit.getContent());
        return values;
    }
}
