package com.junsoo.coupon.domain.campaign;

import com.junsoo.coupon.dto.campaign.AdminCampaignResponse;
import com.junsoo.coupon.dto.campaign.CampaignCreateRequest;
import com.junsoo.coupon.dto.campaign.CampaignResponse;
import com.junsoo.coupon.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final IssueGate issueGate;

    @Transactional
    public AdminCampaignResponse create(CampaignCreateRequest request) {
        // 1. 캠페인을 만든다.
        Campaign campaign = new Campaign(request.name(), request.opensAt(), request.closesAt(), request.totalQuantity());
        // 2. 캠페인을 저장한다.
        campaignRepository.save(campaign);
        // 3. 발급 게이트를 연다. IDENTITY 전략이라 save 뒤에야 id가 생긴다.
        issueGate.initialize(campaign);

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

    // 게이트를 먼저 닫는다. 뒤이은 DB 쓰기가 실패해도 쿠폰은 더 나가지 않는다.
    // 더티체킹에 맡기면 UPDATE가 커밋 시점으로 밀려 이 순서가 지켜지지 않으므로
    // 트랜잭션을 열지 않고 save로 그 자리에서 확정한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminCampaignResponse pause(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        issueGate.updatePaused(campaignId, true);

        campaign.pause();
        return AdminCampaignResponse.from(campaignRepository.save(campaign));
    }

    // 반대로 재개는 DB에 반영된 뒤에 게이트를 연다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminCampaignResponse resume(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        campaign.resume();
        Campaign saved = campaignRepository.save(campaign);

        issueGate.updatePaused(campaignId, false);
        return AdminCampaignResponse.from(saved);
    }

}
