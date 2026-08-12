package com.junsoo.coupon.global.ratelimit;

import com.junsoo.coupon.TestcontainersConfiguration;
import com.junsoo.coupon.domain.campaign.Campaign;
import com.junsoo.coupon.domain.campaign.CampaignRepository;
import com.junsoo.coupon.domain.campaign.IssueGate;
import com.junsoo.coupon.domain.coupon.CouponRepository;
import com.junsoo.coupon.domain.user.Role;
import com.junsoo.coupon.domain.user.User;
import com.junsoo.coupon.domain.user.UserRepository;
import com.junsoo.coupon.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 필터가 체인에 제대로 붙었는지는 HTTP를 실제로 태워야 드러난다 —
// 등록 순서가 어긋나면 SecurityContext가 비어 조용히 통과만 한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "app.ratelimit.issue.limit=3",
        "app.ratelimit.issue.window-seconds=60"   // 창 경계를 밟으면 카운터가 리셋돼 결과가 흔들린다
})
class RateLimitFilterTest {

    private static final int STOCK = 10;
    private static final int TOO_MANY_REQUESTS = 429;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private IssueGate issueGate;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CouponRepository couponRepository;

    private Long campaignId;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
        campaignRepository.deleteAll();
        userRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        Campaign campaign = new Campaign("제한 테스트", now.minusMinutes(1), now.plusHours(1), STOCK);
        campaignId = campaignRepository.save(campaign).getId();
        issueGate.initialize(campaign);
    }

    @Test
    void 제한을_넘으면_429와_Retry_After를_돌려준다() throws Exception {
        Long userId = newUser("burst@test.local");

        for (int i = 1; i <= 3; i++) {
            assertThat(issue(userId).statusCode())
                    .as(i + "번째 요청은 통과해야 한다")
                    .isNotEqualTo(TOO_MANY_REQUESTS);
        }

        HttpResponse<String> blocked = issue(userId);

        assertThat(blocked.statusCode()).isEqualTo(TOO_MANY_REQUESTS);
        assertThat(blocked.headers().firstValue("Retry-After"))
                .as("이게 없으면 클라이언트가 즉시 재시도해 방어가 부하를 늘린다")
                .hasValue("60");
        assertThat(blocked.body()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void 유저가_다르면_바구니도_다르다() throws Exception {
        for (int i = 0; i < 5; i++) {
            Long userId = newUser("solo" + i + "@test.local");

            assertThat(issue(userId).statusCode())
                    .as("VU 1명이 1회만 쏘는 본 측정 시나리오에서는 아무도 걸리지 않아야 한다")
                    .isNotEqualTo(TOO_MANY_REQUESTS);
        }
    }

    @Test
    void 경로를_바꿔가며_쏴도_제한을_피할_수_없다() throws Exception {
        Long userId = newUser("garbage@test.local");

        for (int i = 1; i <= 3; i++) {
            assertThat(post(userId, "abc" + i).statusCode())
                    .as(i + "번째는 아직 예산이 남아 있다")
                    .isNotEqualTo(TOO_MANY_REQUESTS);
        }

        assertThat(post(userId, "abc4").statusCode())
                .as("매번 다른 경로를 써도 같은 바구니에 담겨야 한다")
                .isEqualTo(TOO_MANY_REQUESTS);
    }

    private Long newUser(String email) {
        return userRepository.save(new User(email, "not-used", Role.USER)).getId();
    }

    private HttpResponse<String> issue(Long userId) throws Exception {
        return post(userId, String.valueOf(campaignId));
    }

    private HttpResponse<String> post(Long userId, String campaignSegment) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/campaigns/" + campaignSegment + "/coupons"))
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(userId, Role.USER))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
