package com.junsoo.coupon.domain.campaign;

import com.junsoo.coupon.dto.campaign.AdminCampaignResponse;
import com.junsoo.coupon.dto.campaign.CampaignCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CampaignService {
    private final CampaignRepository campaignRepository;

    @Transactional
    public AdminCampaignResponse create(CampaignCreateRequest request) {
        // 1. 캠페인을 만든다.
        Campaign campaign = new Campaign(request.name(), request.opensAt(), request.closesAt(), request.totalQuantity());
        // 2. 캠페인을 저장한다.
        campaignRepository.save(campaign);

        return AdminCampaignResponse.from(campaign);
    }

}
