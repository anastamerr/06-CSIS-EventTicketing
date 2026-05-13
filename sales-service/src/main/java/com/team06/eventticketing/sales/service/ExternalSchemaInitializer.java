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
        jdbcTemplate.execute("ALTER TABLE promotions ALTER COLUMN current_uses SET DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE promotions ALTER COLUMN active SET DEFAULT TRUE");
        jdbcTemplate.execute("ALTER TABLE promotions ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE promotions ALTER COLUMN metadata SET DEFAULT '{}'::jsonb");
        jdbcTemplate.execute(
                "UPDATE promotions SET created_at = COALESCE(created_at, NOW()) WHERE created_at IS NULL"
        );
        jdbcTemplate.execute("UPDATE promotions SET metadata = COALESCE(metadata, '{}'::jsonb) WHERE metadata IS NULL");
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'promotions'
                          AND column_name = 'discount_type'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE promotions
                        ALTER COLUMN discount_type TYPE promotion_discount_type
                        USING discount_type::promotion_discount_type;
                    END IF;
                END
                $$;
                """);

        jdbcTemplate.execute("""
                ALTER TABLE ticket_sales
                ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
                ALTER COLUMN transaction_details SET DEFAULT '{}'::jsonb
                """);
        jdbcTemplate.execute("""
                UPDATE ticket_sales
                SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
                    transaction_details = COALESCE(transaction_details, '{}'::jsonb)
                WHERE created_at IS NULL
                   OR transaction_details IS NULL
                """);
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'ticket_sales'
                          AND column_name = 'method'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE ticket_sales
                        ALTER COLUMN method TYPE ticket_sale_method
                        USING method::ticket_sale_method;
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
                          AND table_name = 'ticket_sales'
                          AND column_name = 'status'
                          AND data_type <> 'USER-DEFINED'
                    ) THEN
                        ALTER TABLE ticket_sales
                        ALTER COLUMN status TYPE ticket_sale_status
                        USING status::ticket_sale_status;
                    END IF;
                END
                $$;
                """);

        jdbcTemplate.execute("ALTER TABLE sale_promotions ALTER COLUMN applied_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
                "UPDATE sale_promotions SET applied_at = COALESCE(applied_at, CURRENT_TIMESTAMP) WHERE applied_at IS NULL"
        );
    }
}
