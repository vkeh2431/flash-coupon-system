package com.junsoo.coupon.domain.campaign;

import com.junsoo.coupon.domain.coupon.CouponService;
import com.junsoo.coupon.dto.campaign.CampaignResponse;
import com.junsoo.coupon.dto.coupon.CouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
public class CampaignController {
    private final CouponService couponService;
    private final CampaignService campaignService;

    @PostMapping("/{campaignId}/coupons")
    public ResponseEntity<CouponResponse> issue(
            @PathVariable Long campaignId,
            @RequestParam Long userId
            ) {
        CouponResponse response = couponService.issue(campaignId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> findOpenCampaigns() {
        return ResponseEntity.ok(campaignService.findOpenCampaigns());
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> find(
            @PathVariable Long campaignId
    ) {
        return ResponseEntity.ok(campaignService.find(campaignId));
    }

}
