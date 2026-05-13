package com.team06.eventticketing.event.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExternalSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ExternalSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                ALTER TABLE events
                ALTER COLUMN venue SET DEFAULT 'Default Venue',
                ALTER COLUMN event_date SET DEFAULT TIMESTAMP '2026-12-01 00:00:00',
                ALTER COLUMN details SET DEFAULT '{}'::jsonb,
                ALTER COLUMN rating SET DEFAULT 0.0,
                ALTER COLUMN total_ratings SET DEFAULT 0,
                ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP
                """);
        jdbcTemplate.execute("""
                UPDATE events
                SET venue = COALESCE(NULLIF(venue, ''), 'Default Venue'),
                    event_date = COALESCE(event_date, TIMESTAMP '2026-12-01 00:00:00'),
                    details = COALESCE(details, '{}'::jsonb),
                    rating = COALESCE(rating, 0.0),
                    total_ratings = COALESCE(total_ratings, 0),
                    created_at = COALESCE(created_at, CURRENT_TIMESTAMP)
                WHERE venue IS NULL
                   OR venue = ''
                   OR event_date IS NULL
                   OR details IS NULL
                   OR rating IS NULL
                   OR total_ratings IS NULL
                   OR created_at IS NULL
                """);
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'events'
                          AND column_name = 'category'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE events
                        ALTER COLUMN category TYPE event_category
                        USING category::event_category;
                    END IF;
                END
                $$;
                """);
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'events'
                          AND column_name = 'status'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE events
                        ALTER COLUMN status TYPE event_status
                        USING status::event_status;
                    END IF;
                END
                $$;
                """);
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN category SET DEFAULT 'CONCERT'");
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN status SET DEFAULT 'UPCOMING'");

        jdbcTemplate.execute("""
                ALTER TABLE event_sessions
                ALTER COLUMN verified SET DEFAULT FALSE,
                ALTER COLUMN metadata SET DEFAULT '{}'::jsonb,
                ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP
                """);
        jdbcTemplate.execute("""
                UPDATE event_sessions
                SET verified = COALESCE(verified, FALSE),
                    metadata = COALESCE(metadata, '{}'::jsonb),
                    created_at = COALESCE(created_at, CURRENT_TIMESTAMP)
                WHERE verified IS NULL
                   OR metadata IS NULL
                   OR created_at IS NULL
                """);
    }
}
