package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.dto.coupon.CouponResponse;
import com.junsoo.coupon.dto.coupon.MyCouponResponse;
import com.junsoo.coupon.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;

    @GetMapping("/me/coupons")
    public ResponseEntity<List<MyCouponResponse>> findAvailableCoupons(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        List<MyCouponResponse> couponResponseList = couponService.findAvailableCoupons(principal.getUserId());
        return ResponseEntity.ok(couponResponseList);
    }

    @PostMapping ("/coupons/{couponId}/redeem")
    public ResponseEntity<CouponResponse> redeem(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long couponId
    ) {
        return ResponseEntity.ok(couponService.redeem(principal.getUserId(), couponId));
    }

}
