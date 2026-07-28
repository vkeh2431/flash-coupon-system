package com.junsoo.coupon.domain.campaign;

import com.junsoo.coupon.domain.coupon.CouponService;
import com.junsoo.coupon.dto.coupon.CouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
public class CampaignController {
    private final CouponService couponService;

    @PostMapping("/{campaignId}/coupons")
    public ResponseEntity<CouponResponse> issue(
            @PathVariable Long campaignId,
            @RequestParam Long userId
            ) {
        CouponResponse response = couponService.issue(campaignId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


}
