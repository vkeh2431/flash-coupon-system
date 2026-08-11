package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.domain.campaign.Campaign;
import com.junsoo.coupon.domain.campaign.CampaignRepository;
import com.junsoo.coupon.domain.user.User;
import com.junsoo.coupon.domain.user.UserRepository;
import com.junsoo.coupon.dto.coupon.CouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 트랜잭션 경계를 게이트 바깥에 두려고 별도 빈으로 뒀다. 자기 호출은 프록시를 타지 않는다.
@Component
@RequiredArgsConstructor
public class CouponIssuer {

    private final CampaignRepository campaignRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    // 발급 여부는 게이트에서 이미 판정됐다. 여기서 하는 일은 INSERT 하나뿐이므로
    // 연관 엔티티도 프록시로만 세운다 — 실제로 읽지 않으면 SELECT가 나가지 않는다.
    @Transactional
    public CouponResponse insert(Long campaignId, Long userId) {
        Campaign campaign = campaignRepository.getReferenceById(campaignId);
        User user = userRepository.getReferenceById(userId);

        Coupon coupon = new Coupon(user, campaign);
        couponRepository.save(coupon);

        return CouponResponse.from(coupon);
    }
}
