package com.junsoo.coupon.domain.campaign;

import com.junsoo.coupon.dto.campaign.AdminCampaignResponse;
import com.junsoo.coupon.dto.campaign.CampaignCreateRequest;
import com.junsoo.coupon.dto.campaign.CampaignResponse;
import com.junsoo.coupon.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    public List<CampaignResponse> findOpenCampaigns() {
        // 종료되거나, pause되지 않은 캠페인만 보여준다. (즉 현재 날짜 < 종료 날짜 and not pause)
        List<Campaign> campaignList = campaignRepository.findByClosesAtAfterAndPausedFalseOrderByOpensAtAsc(LocalDateTime.now());

        return campaignList.stream()
                .map(CampaignResponse::from)
                .toList();
    }


    public CampaignResponse find(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        return CampaignResponse.from(campaign);
    }

    public List<AdminCampaignResponse> findAllByAdmin() {
        List<Campaign> campaignList = campaignRepository.findAll();

        return campaignList.stream()
                .map(AdminCampaignResponse::from)
                .toList();
    }

}
