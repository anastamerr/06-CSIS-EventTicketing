package com.team06.eventticketing.sales.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class UserJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsById(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
        );
        return count != null && count > 0;
    }

    public Optional<UserSecurityRow> findByIdAndEmail(Long userId, String email) {
        return jdbcTemplate.query(
                """
                SELECT id, email, role
                FROM users
                WHERE id = ? AND LOWER(email) = LOWER(?)
                """,
                (resultSet, rowNum) -> new UserSecurityRow(
                        resultSet.getLong("id"),
                        resultSet.getString("email"),
                        resultSet.getString("role")
                ),
                userId,
                email
        ).stream().findFirst();
    }

    public record UserSecurityRow(Long id, String email, String role) {
    }
}
