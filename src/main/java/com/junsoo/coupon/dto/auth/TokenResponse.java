package com.junsoo.coupon.dto.auth;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String grantType
) {
}
