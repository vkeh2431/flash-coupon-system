package com.junsoo.coupon.dto.campaign;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record CampaignCreateRequest(

    @NotBlank(message = "캠페인 이름은 필수입니다.")
    @Size(min = 2, message = "캠페인 이름은 최소 2자 이상이여야 합니다.")
    String name,

    @NotNull
    LocalDateTime opensAt,

    @NotNull
    LocalDateTime closesAt,

    @Min(value = 100, message = "수량은 100 이상이어야 합니다.")
    @Max(value = 10000, message = "수량은 10000 이하이어야 합니다.")
    int totalQuantity
) {
}
