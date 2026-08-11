package com.junsoo.coupon.domain.campaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByClosesAtAfterAndPausedFalseOrderByOpensAtAsc(LocalDateTime t);
}
