package com.team06.eventticketing.common.auth;

import java.util.Optional;

public interface SecurityUserLookupService {

    Optional<SecurityUserRecord> findByIdAndEmail(Long id, String email);
}
