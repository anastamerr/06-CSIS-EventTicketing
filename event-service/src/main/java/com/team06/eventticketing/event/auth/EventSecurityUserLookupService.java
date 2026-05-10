package com.team06.eventticketing.event.auth;

import com.team06.eventticketing.common.auth.SecurityUserLookupService;
import com.team06.eventticketing.common.auth.SecurityUserRecord;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class EventSecurityUserLookupService implements SecurityUserLookupService {

    @Override
    public Optional<SecurityUserRecord> findByIdAndEmail(Long id, String email) {
        if (id == null || email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SecurityUserRecord(id, email, "AUTHENTICATED"));
    }
}
