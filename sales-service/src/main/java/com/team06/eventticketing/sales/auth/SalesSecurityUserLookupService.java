package com.team06.eventticketing.sales.auth;

import com.team06.eventticketing.common.auth.SecurityUserLookupService;
import com.team06.eventticketing.common.auth.SecurityUserRecord;
import com.team06.eventticketing.contracts.feign.UserServiceClient;
import feign.FeignException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SalesSecurityUserLookupService implements SecurityUserLookupService {

    private final UserServiceClient userServiceClient;

    public SalesSecurityUserLookupService(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Override
    public Optional<SecurityUserRecord> findByIdAndEmail(Long id, String email) {
        try {
            var user = userServiceClient.getUser(id);
            if (user.email() == null || !user.email().equalsIgnoreCase(email)) {
                return Optional.empty();
            }
            return Optional.of(new SecurityUserRecord(user.id(), user.email(), user.role()));
        } catch (FeignException.NotFound exception) {
            return Optional.empty();
        }
    }
}
