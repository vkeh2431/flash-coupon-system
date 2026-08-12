package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.dto.coupon.CouponResponse;

// 응답 본문은 최초 발급 때와 같고 상태 코드만 201/200으로 갈린다.
public record IssueOutcome(CouponResponse coupon, boolean created) {
}
