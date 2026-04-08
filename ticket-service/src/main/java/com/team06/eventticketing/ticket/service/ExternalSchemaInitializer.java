package com.team06.eventticketing.ticket.service;

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
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticket_status') THEN
                        CREATE TYPE ticket_status AS ENUM ('VALID', 'USED', 'EXPIRED', 'CANCELLED');
                    END IF;
                END
                $$;
                """);

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
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS user_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS event_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS status VARCHAR(50)");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS total_amount DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS metadata JSONB");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS booking_date TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS created_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN contact_email SET DEFAULT 'test@events.com'");
        jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN status SET DEFAULT 'PENDING'");
        jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN metadata SET DEFAULT '{}'::jsonb");
        jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN booking_date SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("""
                UPDATE bookings
                SET contact_email = COALESCE(contact_email, 'test@events.com'),
                    status = COALESCE(status, 'PENDING'),
                    metadata = COALESCE(metadata, '{}'::jsonb),
                    booking_date = COALESCE(booking_date, CURRENT_TIMESTAMP),
                    created_at = COALESCE(created_at, booking_date, CURRENT_TIMESTAMP)
                WHERE contact_email IS NULL
                   OR status IS NULL
                   OR metadata IS NULL
                   OR booking_date IS NULL
                   OR created_at IS NULL
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    venue VARCHAR(255) NOT NULL,
                    event_date TIMESTAMP NOT NULL,
                    category VARCHAR(50) DEFAULT 'CONCERT',
                    status VARCHAR(50) NOT NULL DEFAULT 'UPCOMING',
                    rating DOUBLE PRECISION DEFAULT 0.0,
                    total_ratings INTEGER DEFAULT 0,
                    details JSONB DEFAULT '{}'::jsonb,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS name VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS venue VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS event_date TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS category VARCHAR(50)");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS status VARCHAR(50)");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS rating DOUBLE PRECISION");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS total_ratings INTEGER");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS details JSONB");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS created_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN category SET DEFAULT 'CONCERT'");
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN status SET DEFAULT 'UPCOMING'");
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN rating SET DEFAULT 0.0");
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN total_ratings SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN details SET DEFAULT '{}'::jsonb");
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE events ALTER COLUMN category DROP NOT NULL");
        jdbcTemplate.execute("""
                UPDATE events
                SET category = COALESCE(category, 'CONCERT'),
                    status = COALESCE(status, 'UPCOMING'),
                    rating = COALESCE(rating, 0.0),
                    total_ratings = COALESCE(total_ratings, 0),
                    details = COALESCE(details, '{}'::jsonb),
                    created_at = COALESCE(created_at, CURRENT_TIMESTAMP)
                WHERE category IS NULL
                   OR status IS NULL
                   OR rating IS NULL
                   OR total_ratings IS NULL
                   OR details IS NULL
                   OR created_at IS NULL
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tickets (
                    id BIGSERIAL PRIMARY KEY,
                    booking_id BIGINT,
                    attendee_name VARCHAR(255) NOT NULL,
                    ticket_code VARCHAR(255) NOT NULL UNIQUE,
                    status ticket_status NOT NULL DEFAULT 'VALID',
                    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    metadata JSONB
                )
                """);
        jdbcTemplate.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS issued_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS metadata JSONB");
        jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN issued_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("UPDATE tickets SET issued_at = COALESCE(issued_at, CURRENT_TIMESTAMP) WHERE issued_at IS NULL");
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'tickets'
                          AND column_name = 'status'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE tickets
                        ALTER COLUMN status TYPE ticket_status
                        USING status::ticket_status;
                    END IF;
                END
                $$;
                """);
        jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN status SET DEFAULT 'VALID'");
    }
}
