package com.team06.eventticketing.sales.auth;

import com.team06.eventticketing.common.auth.SecurityUserLookupService;
import com.team06.eventticketing.common.auth.SecurityUserRecord;
import com.team06.eventticketing.sales.repository.UserJdbcRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SalesSecurityUserLookupService implements SecurityUserLookupService {

    private final UserJdbcRepository userJdbcRepository;

    public SalesSecurityUserLookupService(UserJdbcRepository userJdbcRepository) {
        this.userJdbcRepository = userJdbcRepository;
    }

    @Override
    public Optional<SecurityUserRecord> findByIdAndEmail(Long id, String email) {
        return userJdbcRepository.findByIdAndEmail(id, email)
                .map(user -> new SecurityUserRecord(user.id(), user.email(), user.role()));
    }
}
