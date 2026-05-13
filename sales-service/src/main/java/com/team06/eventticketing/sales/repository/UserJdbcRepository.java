package com.team06.eventticketing.sales.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserJdbcRepository {

    public UserJdbcRepository(JdbcTemplate jdbcTemplate) {
    }

    public boolean existsById(Long userId) {
        return false;
    }

    public Optional<UserSecurityRow> findByIdAndEmail(Long userId, String email) {
        return Optional.empty();
    }

    public record UserSecurityRow(Long id, String email, String role) {
    }
}
