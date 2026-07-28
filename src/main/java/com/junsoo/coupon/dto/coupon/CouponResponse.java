package com.junsoo.coupon.dto.coupon;

import com.junsoo.coupon.domain.coupon.Coupon;

import java.time.LocalDateTime;

public record CouponResponse(
        Long id,
        LocalDateTime expiresAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getExpiresAt()
        );
    }
}
