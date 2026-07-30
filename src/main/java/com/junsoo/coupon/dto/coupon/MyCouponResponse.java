package com.junsoo.coupon.dto.coupon;

import com.junsoo.coupon.domain.coupon.Coupon;

import java.time.LocalDateTime;

public record MyCouponResponse(
        Long id,
        LocalDateTime expiresAt,
        String campaignName
) {
    public static MyCouponResponse from(Coupon coupon) {
        return new MyCouponResponse(
                coupon.getId(),
                coupon.getExpiresAt(),
                coupon.getCampaign().getName()
        );
    }
}
