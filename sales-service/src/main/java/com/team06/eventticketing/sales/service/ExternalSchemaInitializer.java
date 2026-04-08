package com.team06.eventticketing.sales.service;

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
                    created_at TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bookings (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    event_id BIGINT,
                    contact_email VARCHAR(255) NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    total_amount DOUBLE PRECISION,
                    metadata JSONB,
                    booking_date TIMESTAMP NOT NULL,
                    created_at TIMESTAMP,
                    confirmed_at TIMESTAMP
                )
                """);

        jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS created_at TIMESTAMP");
        jdbcTemplate.execute(
                "UPDATE bookings SET created_at = COALESCE(created_at, booking_date, NOW()) WHERE created_at IS NULL"
        );

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS promotions (
                    id BIGSERIAL PRIMARY KEY,
                    code VARCHAR(255) NOT NULL UNIQUE,
                    discount_type VARCHAR(50) NOT NULL,
                    discount_value DOUBLE PRECISION NOT NULL,
                    max_uses INTEGER NOT NULL,
                    current_uses INTEGER NOT NULL DEFAULT 0,
                    expiry_date TIMESTAMP NOT NULL,
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP,
                    metadata JSONB
                )
                """);

        jdbcTemplate.execute("ALTER TABLE promotions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP");
        jdbcTemplate.execute(
                "UPDATE promotions SET created_at = COALESCE(created_at, NOW()) WHERE created_at IS NULL"
        );
    }
}
