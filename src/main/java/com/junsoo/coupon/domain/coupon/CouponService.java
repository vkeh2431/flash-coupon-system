package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.domain.campaign.IssueGate;
import com.junsoo.coupon.domain.campaign.IssueResult;
import com.junsoo.coupon.dto.coupon.CouponResponse;
import com.junsoo.coupon.dto.coupon.MyCouponResponse;
import com.junsoo.coupon.global.exception.BusinessException;
import com.junsoo.coupon.global.exception.ErrorCode;
import com.junsoo.coupon.global.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponIssuer couponIssuer;
    private final IssueGate issueGate;

    private final Timer gateDuration;
    private final Map<IssueResult, Counter> gateResults = new EnumMap<>(IssueResult.class);

    public CouponService(CouponRepository couponRepository,
                         CouponIssuer couponIssuer,
                         IssueGate issueGate,
                         MeterRegistry meterRegistry) {
        this.couponRepository = couponRepository;
        this.couponIssuer = couponIssuer;
        this.issueGate = issueGate;
        this.gateDuration = Timer.builder("coupon.gate.duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        for (IssueResult result : IssueResult.values()) {
            gateResults.put(result, Counter.builder("coupon.gate.result")
                    .tag("result", result.name())
                    .register(meterRegistry));
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CouponResponse issue(Long campaignId, Long userId) {
        IssueResult result = gateDuration.record(() -> issueGate.tryIssue(campaignId, userId));
        gateResults.get(result).increment();

        if (result != IssueResult.OK) {
            throw toException(campaignId, result);
        }

        return couponIssuer.insert(campaignId, userId);
    }

    private RuntimeException toException(Long campaignId, IssueResult result) {
        return switch (result) {
            case NOT_FOUND -> new ResourceNotFoundException("Campaign", campaignId);
            case NOT_OPENED -> new BusinessException(ErrorCode.CAMPAIGN_NOT_OPENED);
            case CLOSED -> new BusinessException(ErrorCode.CAMPAIGN_CLOSED);
            case PAUSED -> new BusinessException(ErrorCode.CAMPAIGN_PAUSED);
            case ALREADY_ISSUED -> new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
            case OUT_OF_STOCK -> new BusinessException(ErrorCode.CAMPAIGN_OUT_OF_STOCK);
            case OK -> new IllegalStateException("OK는 예외로 변환하지 않는다");
        };
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
