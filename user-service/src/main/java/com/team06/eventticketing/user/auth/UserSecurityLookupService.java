package com.team06.eventticketing.user.auth;

import com.team06.eventticketing.common.auth.SecurityUserLookupService;
import com.team06.eventticketing.common.auth.SecurityUserRecord;
import com.team06.eventticketing.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserSecurityLookupService implements SecurityUserLookupService {

    private final UserRepository userRepository;

    public UserSecurityLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<SecurityUserRecord> findByIdAndEmail(Long id, String email) {
        return userRepository.findById(id)
                .filter(user -> user.getEmail().equalsIgnoreCase(email))
                .map(user -> new SecurityUserRecord(user.getId(), user.getEmail(), user.getRole().name()));
    }
}
