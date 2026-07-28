package com.junsoo.coupon.dto.campaign;

import com.junsoo.coupon.domain.campaign.Campaign;

import java.time.LocalDateTime;

public record CampaignResponse(
        Long id,
        String name,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        int totalQuantity
) {
    public static CampaignResponse from(Campaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getOpensAt(),
                campaign.getClosesAt(),
                campaign.getTotalQuantity()
        );
    }
}