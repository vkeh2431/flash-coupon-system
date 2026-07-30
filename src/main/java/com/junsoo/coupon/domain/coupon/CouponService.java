package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.domain.campaign.Campaign;
import com.junsoo.coupon.domain.campaign.CampaignRepository;
import com.junsoo.coupon.domain.user.User;
import com.junsoo.coupon.domain.user.UserRepository;
import com.junsoo.coupon.dto.coupon.CouponResponse;
import com.junsoo.coupon.dto.coupon.MyCouponResponse;
import com.junsoo.coupon.global.exception.BusinessException;
import com.junsoo.coupon.global.exception.ErrorCode;
import com.junsoo.coupon.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CouponService {
    private final CampaignRepository campaignRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    @Transactional
    public CouponResponse issue(Long campaignId, Long userId) {
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

        if (couponRepository.existsByUserIdAndCampaignId(userId, campaignId)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }

        if(!campaign.tryDecreaseStock()) {
            throw new BusinessException(ErrorCode.CAMPAIGN_OUT_OF_STOCK);
        }


        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Coupon coupon = new Coupon(user, campaign);
        couponRepository.save(coupon);

        return CouponResponse.from(coupon);
    }

    public List<MyCouponResponse> findAvailableCoupons(Long userId) {
        List<Coupon> coupons = couponRepository.findAvailableByUserId(userId, Status.ISSUED);
        return coupons.stream().map(MyCouponResponse::from).toList();
    }
}
