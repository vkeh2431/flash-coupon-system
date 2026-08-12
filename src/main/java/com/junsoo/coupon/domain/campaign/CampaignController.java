package com.junsoo.coupon.domain.campaign;

import com.junsoo.coupon.domain.coupon.CouponService;
import com.junsoo.coupon.domain.coupon.IssueOutcome;
import com.junsoo.coupon.dto.campaign.CampaignResponse;
import com.junsoo.coupon.dto.coupon.CouponResponse;
import com.junsoo.coupon.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal AuthUser principal
            ) {
        IssueOutcome outcome = couponService.issue(campaignId, principal.getUserId());
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.coupon());
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
