package com.junsoo.coupon.dto.campaign;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CampaignCreateRequest(

    @NotBlank(message = "캠페인 이름은 필수입니다.")
    @Size(min = 2, message = "캠페인 이름은 최소 2자 이상이여야 합니다.")
    String name,

    @NotNull
    LocalDateTime opensAt,

    @NotNull
    LocalDateTime closesAt,

    @Min(value = 1, message = "수량은 최소 1 이상이어야 합니다.")
    int totalQuantity
) {
}
