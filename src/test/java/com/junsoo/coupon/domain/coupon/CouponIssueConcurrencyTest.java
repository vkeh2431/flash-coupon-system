package com.junsoo.coupon.domain.coupon;

import com.junsoo.coupon.TestcontainersConfiguration;
import com.junsoo.coupon.domain.campaign.Campaign;
import com.junsoo.coupon.domain.campaign.CampaignRepository;
import com.junsoo.coupon.domain.user.Role;
import com.junsoo.coupon.domain.user.User;
import com.junsoo.coupon.domain.user.UserRepository;
import com.junsoo.coupon.global.exception.BusinessException;
import com.junsoo.coupon.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;


// 발급 경로의 정확성(초과 발급 건수) 검증
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CouponIssueConcurrencyTest {

    private static final int STOCK = 100;

    // 재고보다 많아야 한다 (UNIQUE(user_id, campaign_id) 때문에 유저당 1장)
    private static final int USERS = 300;

    // 더 올리면 데드락이 초과 발급을 가려 테스트가 실패한다.
    private static final int THREADS = Integer.getInteger("threads", 2);

    @Autowired
    private CouponService couponService;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CouponRepository couponRepository;

    private Long campaignId;
    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
        campaignRepository.deleteAll();
        userRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        Campaign campaign = new Campaign("동시성 테스트", now.minusMinutes(1), now.plusHours(1), STOCK);
        campaignId = campaignRepository.save(campaign).getId();

        // 비밀번호는 인코딩하지 않는다 — 이 테스트는 로그인을 거치지 않고 서비스를 직접 호출한다.
        // 측정 대상이 아닌 준비물에 BCrypt 비용을 낼 이유가 없다.
        userIds = new ArrayList<>(USERS);
        for (int i = 0; i < USERS; i++) {
            User user = new User("user" + i + "@test.local", "not-used", Role.USER);
            userIds.add(userRepository.save(user).getId());
        }
    }

    // 버전이 바뀌어도 이 테스트는 하나만 유지하고 아래의 단언만 뒤집는다.
    @Test
    void 동시_발급_시_초과_발급_건수를_검증한다() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(USERS);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            for (Long userId : userIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();                      // 출발선에서 대기
                        couponService.issue(campaignId, userId);
                        success.incrementAndGet();
                    } catch (BusinessException e) {
                        failure.incrementAndGet();
                        if (e.getErrorCode() != ErrorCode.CAMPAIGN_OUT_OF_STOCK
                                && e.getErrorCode() != ErrorCode.COUPON_ALREADY_ISSUED) {
                            System.err.println("예상 못 한 거절: " + e.getErrorCode() + " — " + e.getMessage());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failure.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                        System.err.println("예상 못 한 예외: " + e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(60, TimeUnit.SECONDS);
            if (!finished) {
                // countDown()이 빠진 경로가 있으면 여기서 드러난다.
                System.err.println("⚠️ 60초 안에 끝나지 않았다. 남은 작업: " + doneLatch.getCount());
            }
        } finally {
            executor.shutdown();
        }

        printResult(success.get(), failure.get());

        long issued = couponRepository.count();

        // 버전과 무관한 불변식. 깨지면 발급 로직이 아니라 이 테스트를 의심해야 한다.
        assertThat((long) success.get())
                .as("issue()가 성공 반환한 횟수와 실제 쿠폰 행 수는 같아야 한다")
                .isEqualTo(issued);

        // ─────────────── 버전별 단언 ───────────────
        // v0 : 동시성 제어 없음.
        assertThat(issued)
                .as("v0에서는 재고를 초과해 발급되어야 한다 — 0건이면 코드가 멀쩡한 게 아니라 "
                        + "race window를 못 잡은 것이다. USERS/THREADS를 올리고 「검산식 불일치」를 확인할 것")
                .isGreaterThan(STOCK);

        // v1~v4
        // assertThat(issued)
        //         .as("동시성 제어가 들어간 뒤로는 초과 발급이 0건이어야 한다")
        //         .isEqualTo(STOCK);
        // ──────────────────────────────────────────────────────────
    }


    private void printResult(int success, int failure) {
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow();
        long issued = couponRepository.count();
        int total = campaign.getTotalQuantity();
        int remaining = campaign.getRemainingQuantity();

        System.out.printf("""

                ===== 발급 정확성 검증 =====
                요청           : %d (스레드 풀 %d)
                  성공         : %d
                  실패         : %d
                재고(total)    : %d
                재고(remaining): %d
                COUNT(*)       : %d
                ---------------------------
                초과 발급      : %d   (COUNT(*) - total)
                검산식 불일치  : %d   (total - remaining - COUNT(*), 0이어야 정상)
                ===========================
                %n""",
                USERS, THREADS, success, failure, total, remaining, issued,
                issued - total, (long) (total - remaining) - issued);
    }
}
