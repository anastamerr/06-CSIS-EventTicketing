package com.team06.eventticketing.sales.repository;

import com.team06.eventticketing.sales.model.Promotion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCode(String code);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE p.id = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT p.id, p.code, p.discountType, p.discountValue, p.currentUses,
               COALESCE(SUM(sp.discountApplied), 0.0), p.active, p.expiryDate
        FROM Promotion p
        LEFT JOIN p.salePromotions sp
        GROUP BY p.id, p.code, p.discountType, p.discountValue, p.currentUses, p.active, p.expiryDate
        ORDER BY p.currentUses DESC
        LIMIT :limit
    """)
    List<Object[]> findTopUsedPromotions(@Param("limit") int limit);
}
