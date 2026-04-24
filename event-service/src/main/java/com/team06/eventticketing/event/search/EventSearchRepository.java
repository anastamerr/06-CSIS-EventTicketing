package com.team06.eventticketing.event.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface EventSearchRepository extends ElasticsearchRepository<EventSearchDocument, String> {
}
