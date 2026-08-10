package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.domain.campaign.Campaign;
import com.junsoo.coupon.domain.campaign.CampaignRepository;
import com.junsoo.coupon.domain.user.User;
import com.junsoo.coupon.domain.user.UserRepository;
import com.junsoo.coupon.dto.coupon.CouponResponse;
import com.junsoo.coupon.global.exception.BusinessException;
import com.junsoo.coupon.global.exception.ErrorCode;
import com.junsoo.coupon.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 자기 호출로는 @Transactional 프록시를 타지 않아 별도 빈으로 분리했다.
@Component
@RequiredArgsConstructor
public class CouponIssuer {

    private final CampaignRepository campaignRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    @Transactional
    public CouponResponse issueOnce(Long campaignId, Long userId) {
        int remaining = campaignRepository.findRemainingQuantity(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        if (remaining <= 0) {
            throw new BusinessException(ErrorCode.CAMPAIGN_OUT_OF_STOCK);
        }

        if (couponRepository.existsByUserIdAndCampaignId(userId, campaignId)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime opensAt = campaign.getOpensAt();
        LocalDateTime closesAt = campaign.getClosesAt();
        if(now.isBefore(opensAt)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_NOT_OPENED);
        }
        if(now.isAfter(closesAt)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_CLOSED);
        }

        if(campaign.isPaused()) throw new BusinessException(ErrorCode.CAMPAIGN_PAUSED);

        if(!campaign.tryDecreaseStock()) {
            throw new BusinessException(ErrorCode.CAMPAIGN_OUT_OF_STOCK);
        }

        // 재고 UPDATE를 쿠폰 INSERT보다 먼저 내보낸다. 순서가 반대면 INSERT의 FK가
        // 캠페인 행에 S락을 걸고 뒤따르는 UPDATE가 X락으로 승격하면서 데드락이 난다.
        campaignRepository.saveAndFlush(campaign);

        User user = userRepository.getReferenceById(userId);
        Coupon coupon = new Coupon(user, campaign);
        couponRepository.save(coupon);

        return CouponResponse.from(coupon);
    }
}
