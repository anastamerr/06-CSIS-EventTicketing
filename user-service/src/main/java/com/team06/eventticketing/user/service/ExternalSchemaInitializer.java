package com.team06.eventticketing.user.service;

import com.team06.eventticketing.user.model.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class ExternalSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public ExternalSchemaInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
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
        jdbcTemplate.query(
                "SELECT id, password FROM users WHERE password IS NOT NULL",
                rs -> {
                    String password = rs.getString("password");
                    if (!isBcryptHash(password)) {
                        jdbcTemplate.update(
                                "UPDATE users SET password = ? WHERE id = ?",
                                passwordEncoder.encode(password),
                                rs.getLong("id"));
                    }
                });
        Integer seededAdminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class,
                "admin@eventticketing.local");
        if (seededAdminCount != null && seededAdminCount == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO users (name, email, password, phone, role, status, preferences, created_at)
                    VALUES (?, ?, ?, ?, ?::user_role, ?::user_status, '{}'::jsonb, CURRENT_TIMESTAMP)
                    """,
                    "Seed Admin",
                    "admin@eventticketing.local",
                    passwordEncoder.encode("admin123"),
                    "01000000000",
                    UserRole.ADMIN.name(),
                    "ACTIVE");
        } else {
            jdbcTemplate.update(
                    """
                    UPDATE users
                    SET password = ?,
                        role = ?::user_role,
                        status = ?::user_status
                    WHERE email = ?
                    """,
                    passwordEncoder.encode("admin123"),
                    UserRole.ADMIN.name(),
                    "ACTIVE",
                    "admin@eventticketing.local");
        }

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

    private boolean isBcryptHash(String password) {
        return password != null && password.matches("^\\$2[aby]\\$\\d\\d\\$.{53}$");
    }
}
