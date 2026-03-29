package com.team06.eventticketing.user.repository;

import com.team06.eventticketing.user.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
            SELECT
                u.id AS user_id,
                u.name AS name,
                COALESCE(SUM(COALESCE(b.total_amount, 0)), 0) AS total_spent,
                COUNT(b.id) AS booking_count
            FROM users u
            JOIN bookings b ON b.user_id = u.id
            WHERE b.status = 'COMPLETED'
              AND b.booking_date >= :startInclusive
              AND b.booking_date < :endExclusive
            GROUP BY u.id, u.name
            ORDER BY total_spent DESC, booking_count DESC, u.id ASC
            """, nativeQuery = true)
    List<Object[]> findTopAttendeesBySpending(@Param("startInclusive") LocalDateTime startInclusive,
                                              @Param("endExclusive") LocalDateTime endExclusive,
                                              Pageable pageable);

    @Query(value = """
            SELECT
                u.id AS user_id,
                u.name AS name,
                COUNT(b.id) AS total_bookings,
                COALESCE(SUM(CASE WHEN b.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_bookings,
                COALESCE(SUM(CASE WHEN b.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_bookings,
                COALESCE(SUM(CASE WHEN b.status = 'COMPLETED' THEN COALESCE(b.total_amount, 0) ELSE 0 END), 0) AS total_spent,
                COALESCE(AVG(CASE WHEN b.status = 'COMPLETED' THEN COALESCE(b.total_amount, 0) END), 0) AS average_booking_amount
            FROM users u
            LEFT JOIN bookings b ON b.user_id = u.id
            WHERE u.id = :userId
            GROUP BY u.id, u.name
            """, nativeQuery = true)
    List<Object[]> findBookingSummaryByUserId(@Param("userId") Long userId);
}
