package com.team06.eventticketing.user.repository;

import com.team06.eventticketing.user.model.FavoriteVenue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FavoriteVenueRepository extends JpaRepository<FavoriteVenue, Long> {

    List<FavoriteVenue> findByUserId(Long userId);

    List<FavoriteVenue> findByUserIdOrderByIdAsc(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE FavoriteVenue f SET f.isDefault = false WHERE f.user.id = :userId")
    void clearDefaultsForUser(@Param("userId") Long userId);
}
