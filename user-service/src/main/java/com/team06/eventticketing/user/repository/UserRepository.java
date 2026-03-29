package com.team06.eventticketing.user.repository;

import com.team06.eventticketing.user.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.favoriteVenues WHERE u.id = :id")
    Optional<User> findByIdWithFavoriteVenues(@Param("id") Long id);

    @Query(value = "SELECT * FROM users WHERE preferences->>:key = :value", nativeQuery = true)
    List<User> findByPreferenceKeyValue(@Param("key") String key, @Param("value") String value);
}
