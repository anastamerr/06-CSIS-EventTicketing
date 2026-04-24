package com.team06.eventticketing.event.auth;

import com.team06.eventticketing.common.auth.SecurityUserLookupService;
import com.team06.eventticketing.common.auth.SecurityUserRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventSecurityUserLookupService implements SecurityUserLookupService {

    private final JdbcTemplate jdbcTemplate;

    public EventSecurityUserLookupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SecurityUserRecord> findByIdAndEmail(Long id, String email) {
        return jdbcTemplate.query(
                "SELECT id, email, role FROM users WHERE id = ? AND LOWER(email) = LOWER(?)",
                this::mapUsers,
                id,
                email)
                .stream()
                .findFirst();
    }

    private SecurityUserRecord mapUsers(ResultSet resultSet, int rowNum) throws SQLException {
        return new SecurityUserRecord(
                resultSet.getLong("id"),
                resultSet.getString("email"),
                resultSet.getString("role"));
    }
}
