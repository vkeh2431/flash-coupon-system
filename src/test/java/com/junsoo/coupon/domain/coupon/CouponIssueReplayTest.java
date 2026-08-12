package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.TestcontainersConfiguration;
import com.junsoo.coupon.domain.campaign.Campaign;
import com.junsoo.coupon.domain.campaign.CampaignRepository;
import com.junsoo.coupon.domain.campaign.IssueGate;
import com.junsoo.coupon.domain.user.Role;
import com.junsoo.coupon.domain.user.User;
import com.junsoo.coupon.domain.user.UserRepository;
import com.junsoo.coupon.global.exception.BusinessException;
import com.junsoo.coupon.global.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 응답을 놓친 클라이언트의 재시도 경로 검증.
// 동시성 테스트는 유저가 전부 달라 ALREADY_ISSUED 분기를 한 번도 타지 않는다.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CouponIssueReplayTest {

    private static final int STOCK = 10;

    @Autowired
    private CouponService couponService;
    @Autowired
    private IssueGate issueGate;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private MeterRegistry meterRegistry;

    private Long campaignId;
    private Long userId;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
        campaignRepository.deleteAll();
        userRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        Campaign campaign = new Campaign("재시도 테스트", now.minusMinutes(1), now.plusHours(1), STOCK);
        campaignId = campaignRepository.save(campaign).getId();
        issueGate.initialize(campaign);

        userId = userRepository.save(new User("replay@test.local", "not-used", Role.USER)).getId();
    }

    @Test
    void 같은_유저가_다시_요청하면_최초_발급_결과를_그대로_돌려준다() {
        IssueOutcome first = couponService.issue(campaignId, userId);
        IssueOutcome second = couponService.issue(campaignId, userId);

        assertThat(first.created()).as("첫 요청은 새로 만든다").isTrue();
        assertThat(second.created()).as("재시도는 재생이다 — 컨트롤러가 200으로 내려준다").isFalse();
        assertThat(second.coupon())
                .as("응답 본문이 최초 발급과 같아야 재시도한 쪽이 자기 쿠폰을 알 수 있다")
                .isEqualTo(first.coupon());

        assertThat(couponRepository.count()).as("재생은 쿠폰을 만들지 않는다").isEqualTo(1);
        assertThat(issueGate.remainingStock(campaignId)).as("재고도 한 번만 깎인다").isEqualTo(STOCK - 1);
    }

    @Test
    void 게이트는_발급됐다는데_쿠폰이_없으면_거절하고_카운터를_올린다() {
        couponService.issue(campaignId, userId);

        // INSERT가 끝내 실패해 쿠폰만 없는 상태를 만든다. Redis 명단에는 userId가 남는다.
        couponRepository.deleteAll();

        double before = meterRegistry.counter("coupon.replay.missing").count();

        assertThatThrownBy(() -> couponService.issue(campaignId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_ISSUED);

        assertThat(meterRegistry.counter("coupon.replay.missing").count())
                .as("재고와 쿠폰이 어긋난 상태는 세지 않으면 조용히 지나간다")
                .isEqualTo(before + 1);
    }
}
