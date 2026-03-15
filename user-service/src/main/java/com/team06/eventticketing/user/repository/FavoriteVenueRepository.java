package com.team06.eventticketing.user.repository;

import com.team06.eventticketing.user.model.FavoriteVenue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteVenueRepository extends JpaRepository<FavoriteVenue, Long> {

    List<FavoriteVenue> findByUserId(Long userId);

    List<FavoriteVenue> findByUserIdOrderByIdAsc(Long userId);
}
