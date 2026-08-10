package com.junsoo.coupon.domain.campaign;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByClosesAtAfterAndPausedFalseOrderByOpensAtAsc(LocalDateTime t);

    // 엔티티가 아니라 스칼라로 읽는다. 엔티티로 읽으면 1차 캐시에 남아
    // 뒤따르는 findByIdForUpdate가 락은 잡되 캐시의 낡은 값을 돌려준다.
    @Query("select c.remainingQuantity from Campaign c where c.id = :id")
    Optional<Integer> findRemainingQuantity(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> findByIdForUpdate(@Param("id") Long id);
}
