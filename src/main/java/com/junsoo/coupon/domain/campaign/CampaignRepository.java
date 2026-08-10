package com.junsoo.coupon.domain.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByClosesAtAfterAndPausedFalseOrderByOpensAtAsc(LocalDateTime t);

    @Query("select c.remainingQuantity from Campaign c where c.id = :id")
    Optional<Integer> findRemainingQuantity(@Param("id") Long id);
}
