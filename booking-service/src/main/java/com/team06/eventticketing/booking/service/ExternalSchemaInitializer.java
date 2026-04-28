package com.team06.eventticketing.booking.service;

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
                ALTER TABLE bookings
                ALTER COLUMN metadata SET DEFAULT '{}'::jsonb,
                ALTER COLUMN booking_date SET DEFAULT CURRENT_TIMESTAMP,
                ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP
                """);
        jdbcTemplate.execute("""
                UPDATE bookings
                SET metadata = COALESCE(metadata, '{}'::jsonb),
                    booking_date = COALESCE(booking_date, CURRENT_TIMESTAMP),
                    created_at = COALESCE(created_at, booking_date, CURRENT_TIMESTAMP)
                WHERE metadata IS NULL
                   OR booking_date IS NULL
                   OR created_at IS NULL
                """);
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'bookings'
                          AND column_name = 'status'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE bookings
                        ALTER COLUMN status DROP DEFAULT,
                        ALTER COLUMN status TYPE booking_status
                        USING status::booking_status;
                        ALTER TABLE bookings
                        ALTER COLUMN status SET DEFAULT 'PENDING'::booking_status;
                    END IF;
                END
                $$;
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255),
                    venue VARCHAR(255),
                    category VARCHAR(50),
                    status VARCHAR(50) NOT NULL,
                    event_date TIMESTAMP,
                    details JSONB
                )
                """);
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS name VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS venue VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS category VARCHAR(50)");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS event_date TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS details JSONB");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS event_sessions (
                    id BIGSERIAL PRIMARY KEY,
                    event_id BIGINT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    speaker VARCHAR(255),
                    start_time TIMESTAMP NOT NULL,
                    end_time TIMESTAMP NOT NULL,
                    capacity INTEGER NOT NULL,
                    verified BOOLEAN NOT NULL DEFAULT FALSE,
                    metadata JSONB,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tickets (
                    id BIGSERIAL PRIMARY KEY,
                    booking_id BIGINT NOT NULL,
                    attendee_name VARCHAR(255) NOT NULL,
                    ticket_code VARCHAR(255) NOT NULL UNIQUE,
                    status VARCHAR(50) NOT NULL,
                    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    metadata JSONB
                )
                """);
        jdbcTemplate.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS issued_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN issued_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("UPDATE tickets SET issued_at = COALESCE(issued_at, CURRENT_TIMESTAMP) WHERE issued_at IS NULL");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ticket_sales (
                    id BIGSERIAL PRIMARY KEY,
                    booking_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    amount NUMERIC(19, 4) NOT NULL,
                    method VARCHAR(50) NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    transaction_details JSONB,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("ALTER TABLE ticket_sales ADD COLUMN IF NOT EXISTS created_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE ticket_sales ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
                "UPDATE ticket_sales SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE created_at IS NULL"
        );

        jdbcTemplate.execute("""
                ALTER TABLE booking_items
                ALTER COLUMN metadata SET DEFAULT '{}'::jsonb
                """);
        jdbcTemplate.execute("UPDATE booking_items SET metadata = COALESCE(metadata, '{}'::jsonb) WHERE metadata IS NULL");
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'booking_items'
                          AND column_name = 'status'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE booking_items
                        ALTER COLUMN status DROP DEFAULT,
                        ALTER COLUMN status TYPE booking_item_status
                        USING status::booking_item_status;
                        ALTER TABLE booking_items
                        ALTER COLUMN status SET DEFAULT 'RESERVED'::booking_item_status;
                    END IF;
                END
                $$;
                """);
    }
}
