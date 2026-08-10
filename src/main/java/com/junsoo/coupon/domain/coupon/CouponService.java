package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.dto.coupon.CouponResponse;
import com.junsoo.coupon.dto.coupon.MyCouponResponse;
import com.junsoo.coupon.global.exception.BusinessException;
import com.junsoo.coupon.global.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@Transactional(readOnly = true)
public class CouponService {

    private static final int MAX_ATTEMPTS = 50;

    private final CouponRepository couponRepository;
    private final CouponIssuer couponIssuer;

    private final Counter retries;
    private final Counter retryExhausted;
    private final DistributionSummary attemptsUntilSuccess;

    public CouponService(CouponRepository couponRepository,
                         CouponIssuer couponIssuer,
                         MeterRegistry meterRegistry) {
        this.couponRepository = couponRepository;
        this.couponIssuer = couponIssuer;
        this.retries = meterRegistry.counter("coupon.issue.retries");
        this.retryExhausted = meterRegistry.counter("coupon.issue.retry.exhausted");
        this.attemptsUntilSuccess = DistributionSummary.builder("coupon.issue.attempts")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // 바깥에 트랜잭션이 열리면 재시도가 같은 트랜잭션에 참여하고, 커넥션도 내내 붙잡는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CouponResponse issue(Long campaignId, Long userId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                CouponResponse response = couponIssuer.issueOnce(campaignId, userId);
                attemptsUntilSuccess.record(attempt);
                return response;
            } catch (OptimisticLockingFailureException e) {
                retries.increment();
            }
        }

        retryExhausted.increment();
        throw new BusinessException(ErrorCode.ISSUE_RETRY_EXHAUSTED);
    }

    public List<MyCouponResponse> findAvailableCoupons(Long userId) {
        List<Coupon> coupons = couponRepository.findAvailableByUserId(userId, Status.ISSUED);
        return coupons.stream().map(MyCouponResponse::from).toList();
    }

    @Transactional
    public CouponResponse redeem(Long userId, Long couponId) {
        Coupon coupon = couponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "쿠폰 조회 실패: couponID: " + couponId + ", userId: " + userId));

        if(coupon.getStatus().equals(Status.REDEEMED)) throw new BusinessException(ErrorCode.COUPON_ALREADY_REDEEMED);
        if(coupon.getStatus().equals(Status.REVOKED)) throw new BusinessException(ErrorCode.COUPON_REVOKED);
        if(coupon.isExpired()) throw new BusinessException(ErrorCode.COUPON_EXPIRED);

        coupon.redeem();

        return CouponResponse.from(coupon);
    }
}
