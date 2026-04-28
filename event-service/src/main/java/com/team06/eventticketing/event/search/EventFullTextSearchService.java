package com.team06.eventticketing.event.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.team06.eventticketing.event.model.Event;
import com.team06.eventticketing.event.model.EventCategory;
import com.team06.eventticketing.event.model.EventStatus;
import com.team06.eventticketing.event.repository.EventRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EventFullTextSearchService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_RESULTS = 200;

    private final ElasticsearchOperations elasticsearchOperations;
    private final EventRepository eventRepository;

    public EventFullTextSearchService(
            ElasticsearchOperations elasticsearchOperations,
            EventRepository eventRepository
    ) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.eventRepository = eventRepository;
    }

    public List<Event> search(
            String query,
            EventCategory category,
            String venue,
            EventStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Double minRating,
            Double maxRating
    ) {
        SearchHits<EventSearchDocument> hits = elasticsearchOperations.search(
                buildQuery(query, category, venue, status, startDate, endDate, minRating, maxRating),
                EventSearchDocument.class
        );

        List<Long> orderedEventIds = hits.getSearchHits().stream()
                .map(searchHit -> parseId(searchHit.getContent().getId()))
                .filter(Objects::nonNull)
                .toList();

        if (orderedEventIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Event> eventsById = eventRepository.findAllById(orderedEventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event, (left, right) -> left, LinkedHashMap::new));

        return orderedEventIds.stream()
                .map(eventsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private NativeQuery buildQuery(
            String query,
            EventCategory category,
            String venue,
            EventStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Double minRating,
            Double maxRating
    ) {
        List<Query> filters = new ArrayList<>();

        if (category != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("category").value(category.name()))));
        }

        if (StringUtils.hasText(venue)) {
            filters.add(Query.of(q -> q.matchPhrase(m -> m.field("venue").query(venue.trim()))));
        }

        if (status != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("status").value(status.name()))));
        }

        if (startDate != null || endDate != null) {
            String startDateTime = startDate == null ? null : startDate.atStartOfDay().format(DATE_TIME_FORMATTER);
            String endDateTime = endDate == null ? null : endDate.atTime(LocalTime.MAX).format(DATE_TIME_FORMATTER);
            filters.add(Query.of(q -> q.range(r -> r.date(d -> {
                d.field("eventDate");
                if (startDateTime != null) {
                    d.gte(startDateTime);
                }
                if (endDateTime != null) {
                    d.lte(endDateTime);
                }
                return d;
            }))));
        }

        if (minRating != null || maxRating != null) {
            filters.add(Query.of(q -> q.range(r -> r.number(n -> {
                n.field("rating");
                if (minRating != null) {
                    n.gte(minRating);
                }
                if (maxRating != null) {
                    n.lte(maxRating);
                }
                return n;
            }))));
        }

        NativeQueryBuilder builder = NativeQuery.builder()
                .withPageable(PageRequest.of(0, MAX_RESULTS))
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)));

        if (StringUtils.hasText(query)) {
            String fullText = query.trim();
            builder.withQuery(q -> q.bool(b -> b
                    .should(s -> s.matchPhrasePrefix(m -> m.field("name").query(fullText).boost(4.0f)))
                    .should(s -> s.matchPhrasePrefix(m -> m.field("description").query(fullText).boost(2.5f)))
                    .should(s -> s.matchPhrasePrefix(m -> m.field("venue").query(fullText).boost(3.0f)))
                    .should(s -> s.multiMatch(m -> m
                            .query(fullText)
                            .fields("name^3", "description^2", "venue^2")
                            .fuzziness("AUTO")))
                    .minimumShouldMatch("1")));
        } else {
            builder.withQuery(q -> q.matchAll(m -> m));
        }

        if (!filters.isEmpty()) {
            builder.withFilter(f -> f.bool(b -> b.filter(filters)));
        }

        return builder.build();
    }

    private Long parseId(String id) {
        try {
            return id == null ? null : Long.parseLong(id);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
