package com.team06.eventticketing.user.repository;

import com.team06.eventticketing.user.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
    @Query(value = "SELECT * FROM users WHERE preferences->?1 = to_jsonb(?2::text)", nativeQuery = true)
    List<User> findByPreferenceKeyValue(String key, String value);
}
