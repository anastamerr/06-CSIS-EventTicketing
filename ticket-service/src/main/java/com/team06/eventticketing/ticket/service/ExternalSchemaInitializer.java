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
                CREATE TABLE IF NOT EXISTS tickets (
                    id BIGSERIAL PRIMARY KEY,
                    booking_id BIGINT,
                    event_id BIGINT,
                    attendee_name VARCHAR(255) NOT NULL,
                    ticket_code VARCHAR(255) NOT NULL UNIQUE,
                    status ticket_status NOT NULL DEFAULT 'VALID',
                    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    metadata JSONB
                )
                """);
        jdbcTemplate.execute("ALTER TABLE tickets ADD COLUMN IF NOT EXISTS event_id BIGINT");
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
