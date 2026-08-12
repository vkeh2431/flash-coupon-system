package com.junsoo.coupon.global.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 창을 늘리거나 limit을 키우면 고정 윈도우의 경계 오차도 같이 커진다 —
// 경계에서 한 창치를 몰아 쓸 수 있으므로 최악값은 limit의 2배다.
@ConfigurationProperties(prefix = "app.ratelimit.issue")
public record RateLimitProperties(
        @DefaultValue("5") int limit,
        @DefaultValue("1") int windowSeconds
) {
}
