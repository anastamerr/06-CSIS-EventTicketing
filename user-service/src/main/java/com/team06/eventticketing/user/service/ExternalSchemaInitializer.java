package com.team06.eventticketing.user.service;

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
                ALTER TABLE users
                ALTER COLUMN password DROP NOT NULL,
                ALTER COLUMN preferences SET DEFAULT '{}'::jsonb,
                ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP
                """);
        jdbcTemplate.execute("UPDATE users SET preferences = COALESCE(preferences, '{}'::jsonb) WHERE preferences IS NULL");
        jdbcTemplate.execute("UPDATE users SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE created_at IS NULL");
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'users'
                          AND column_name = 'role'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE users
                        ALTER COLUMN role TYPE user_role
                        USING role::user_role;
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
                          AND table_name = 'users'
                          AND column_name = 'status'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE users
                        ALTER COLUMN status TYPE user_status
                        USING status::user_status;
                    END IF;
                END
                $$;
                """);
        jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE'");

        jdbcTemplate.execute("""
                ALTER TABLE favorite_venues
                ALTER COLUMN is_default SET DEFAULT FALSE,
                ALTER COLUMN metadata SET DEFAULT '{}'::jsonb,
                ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP
                """);
        jdbcTemplate.execute("""
                UPDATE favorite_venues
                SET is_default = COALESCE(is_default, FALSE),
                    metadata = COALESCE(metadata, '{}'::jsonb),
                    created_at = COALESCE(created_at, CURRENT_TIMESTAMP)
                WHERE is_default IS NULL
                   OR metadata IS NULL
                   OR created_at IS NULL
                """);
    }
}
