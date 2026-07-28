package com.junsoo.coupon.dto.campaign;

import com.junsoo.coupon.domain.campaign.Campaign;

import java.time.LocalDateTime;

public record AdminCampaignResponse(
        Long id,
        String name,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        int totalQuantity,
        int remainingQuantity,
        boolean paused
) {
    public static AdminCampaignResponse from(Campaign campaign) {
        return new AdminCampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getOpensAt(),
                campaign.getClosesAt(),
                campaign.getTotalQuantity(),
                campaign.getRemainingQuantity(),
                campaign.isPaused()
        );
    }
}
