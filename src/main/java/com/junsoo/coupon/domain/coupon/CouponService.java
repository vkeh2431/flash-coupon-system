package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.dto.coupon.CouponResponse;
import com.junsoo.coupon.dto.coupon.MyCouponResponse;
import com.junsoo.coupon.global.exception.BusinessException;
import com.junsoo.coupon.global.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@Transactional(readOnly = true)
public class CouponService {

    private static final String LOCK_KEY_PREFIX = "lock:campaign:";
    private static final long LOCK_WAIT_SECONDS = 10;

    private final CouponRepository couponRepository;
    private final CouponIssuer couponIssuer;
    private final RedissonClient redissonClient;

    private final Timer lockWait;
    private final Counter lockTimeouts;

    public CouponService(CouponRepository couponRepository,
                         CouponIssuer couponIssuer,
                         RedissonClient redissonClient,
                         MeterRegistry meterRegistry) {
        this.couponRepository = couponRepository;
        this.couponIssuer = couponIssuer;
        this.redissonClient = redissonClient;
        this.lockWait = Timer.builder("coupon.lock.wait")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        this.lockTimeouts = meterRegistry.counter("coupon.lock.timeout");
    }

    // 락 해제가 커밋보다 앞서면 그 틈에 다른 스레드가 낡은 재고를 읽는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CouponResponse issue(Long campaignId, Long userId) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + campaignId);
        boolean acquired;
        long startedAt = System.nanoTime();
        try {
            // leaseTime을 주지 않으면 watchdog이 락을 갱신한다. 임계 구역에 DB 쓰기가 있어
            // 고정 만료를 걸면 작업 도중 락이 풀려 초과 발급이 난다.
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.ISSUE_LOCK_TIMEOUT);
        } finally {
            lockWait.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }

        if (!acquired) {
            lockTimeouts.increment();
            throw new BusinessException(ErrorCode.ISSUE_LOCK_TIMEOUT);
        }

        try {
            return couponIssuer.issueOnce(campaignId, userId);
        } finally {
            lock.unlock();
        }
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
