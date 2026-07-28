package com.junsoo.coupon.domain.admin;


import com.junsoo.coupon.domain.campaign.CampaignService;
import com.junsoo.coupon.dto.campaign.AdminCampaignResponse;
import com.junsoo.coupon.dto.campaign.CampaignCreateRequest;
import com.junsoo.coupon.dto.campaign.CampaignResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CampaignService campaignService;

    @PostMapping("/campaigns")
    public ResponseEntity<AdminCampaignResponse> createCampaign(
            @Valid @RequestBody CampaignCreateRequest request
    ) {
       AdminCampaignResponse response = campaignService.create(request);
       return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<AdminCampaignResponse>> findAll() {
        return ResponseEntity.ok(campaignService.findAllByAdmin());
    }

}
