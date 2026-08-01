package com.junsoo.coupon.dto.coupon;

import com.junsoo.coupon.domain.coupon.Coupon;
import com.junsoo.coupon.domain.coupon.Status;

import java.time.LocalDateTime;

public record CouponResponse(
        Long id,
        Status status,
        LocalDateTime redeemedAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getStatus(),
                coupon.getRedeemedAt()
        );
    }
}
