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
