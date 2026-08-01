package com.junsoo.coupon.domain.admin;


import com.junsoo.coupon.domain.campaign.CampaignService;
import com.junsoo.coupon.dto.campaign.AdminCampaignResponse;
import com.junsoo.coupon.dto.campaign.CampaignCreateRequest;
import com.junsoo.coupon.dto.campaign.CampaignResponse;
import com.junsoo.coupon.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/campaigns")
@RequiredArgsConstructor
public class AdminController {

    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<AdminCampaignResponse> createCampaign(
            @Valid @RequestBody CampaignCreateRequest request
    ) {
       AdminCampaignResponse response = campaignService.create(request);
       return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AdminCampaignResponse>> findAll() {
        return ResponseEntity.ok(campaignService.findAllByAdmin());
    }

    @PostMapping("/{campaignId}/pause")
    public ResponseEntity<AdminCampaignResponse> pause(
            @PathVariable Long campaignId
    ) {
        return ResponseEntity.ok(campaignService.pause(campaignId));
    }

    @PostMapping("/{campaignId}/resume")
    public ResponseEntity<AdminCampaignResponse> resume(
            @PathVariable Long campaignId
    ) {
        return ResponseEntity.ok(campaignService.resume(campaignId));
    }

}
