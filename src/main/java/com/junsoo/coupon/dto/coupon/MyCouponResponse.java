package com.junsoo.coupon.dto.coupon;

import com.junsoo.coupon.domain.coupon.Coupon;
import com.junsoo.coupon.domain.coupon.Status;

import java.time.LocalDateTime;

public record MyCouponResponse(
        Long id,
        String campaignName,
        LocalDateTime expiresAt
) {
    public static MyCouponResponse from(Coupon coupon) {
        return new MyCouponResponse(
                coupon.getId(),
                coupon.getCampaign().getName(),
                coupon.getExpiresAt()
        );
    }
}
