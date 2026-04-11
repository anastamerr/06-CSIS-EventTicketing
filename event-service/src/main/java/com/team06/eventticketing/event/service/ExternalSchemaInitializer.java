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
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255) UNIQUE,
                    password VARCHAR(255),
                    phone VARCHAR(255) UNIQUE,
                    role VARCHAR(50),
                    status VARCHAR(50),
                    preferences JSONB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(50)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS status VARCHAR(50)");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    event_id BIGINT,
                    contact_email VARCHAR(255) NOT NULL DEFAULT 'test@events.com',
                    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                    total_amount DOUBLE PRECISION,
                    metadata JSONB DEFAULT '{}'::jsonb,
                    booking_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    confirmed_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS event_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS total_amount DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS booking_date TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN booking_date SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("""
                UPDATE bookings
                SET booking_date = COALESCE(booking_date, CURRENT_TIMESTAMP),
                    created_at = COALESCE(created_at, booking_date, CURRENT_TIMESTAMP)
                WHERE booking_date IS NULL
                   OR created_at IS NULL
                """);

        jdbcTemplate.execute("""
                ALTER TABLE events
                ALTER COLUMN details SET DEFAULT '{}'::jsonb,
                ALTER COLUMN rating SET DEFAULT 0.0,
                ALTER COLUMN total_ratings SET DEFAULT 0,
                ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP
                """);
        jdbcTemplate.execute("""
                UPDATE events
                SET details = COALESCE(details, '{}'::jsonb),
                    rating = COALESCE(rating, 0.0),
                    total_ratings = COALESCE(total_ratings, 0),
                    created_at = COALESCE(created_at, CURRENT_TIMESTAMP)
                WHERE details IS NULL
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
