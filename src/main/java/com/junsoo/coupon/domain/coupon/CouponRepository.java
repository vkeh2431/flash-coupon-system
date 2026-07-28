package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.domain.campaign.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    boolean existsByUserIdAndCampaignId(Long userId, Long CampaignId);
}
