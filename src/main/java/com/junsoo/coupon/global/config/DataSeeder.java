package com.junsoo.coupon.global.config;

import com.junsoo.coupon.domain.campaign.Campaign;
import com.junsoo.coupon.domain.campaign.CampaignRepository;
import com.junsoo.coupon.domain.campaign.IssueGate;
import com.junsoo.coupon.domain.user.Role;
import com.junsoo.coupon.domain.user.User;
import com.junsoo.coupon.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 개발 환경에서 최초 관리자 계정과 테스트용 캠페인을 만들어 둔다.
 * 가입 API로는 ADMIN 권한을 얻을 수 없으므로(권한 상승 차단),
 * 첫 관리자는 애플리케이션 기동 과정에서만 생성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final IssueGate issueGate;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email:admin@coupon.local}")
    private String adminEmail;

    @Value("${app.seed.admin-password:admin1234}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        seedAdmin();
        seedCampaigns();
    }

    private void seedAdmin() {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("[seed] 관리자 계정이 이미 있어 건너뜀: {}", adminEmail);
            return;
        }

        User admin = new User(adminEmail, passwordEncoder.encode(adminPassword), Role.ADMIN);
        userRepository.save(admin);
        log.info("[seed] 관리자 계정 생성: {} / 비밀번호는 app.seed.admin-password 값", adminEmail);
    }

    private void seedCampaigns() {
        if (campaignRepository.count() > 0) {
            log.info("[seed] 캠페인이 이미 있어 건너뜀");
            return;
        }

        // 절대 날짜로 박으면 시간이 지나 전부 '종료' 상태가 되므로 현재 시각 기준 상대값으로 만든다.
        LocalDateTime now = LocalDateTime.now();
        seed(new Campaign("진행중 캠페인", now.minusDays(1), now.plusDays(30), 100));
        seed(new Campaign("예정 캠페인", now.plusDays(7), now.plusDays(14), 50));
        seed(new Campaign("종료 캠페인", now.minusDays(30), now.minusDays(1), 30));
        log.info("[seed] 테스트 캠페인 3건 생성 (진행중 / 예정 / 종료)");
    }

    // DB는 ddl-auto로 비워지지만 Redis는 앱을 재기동해도 남는다.
    // initialize가 이전 실행의 발급 이력까지 지운다.
    private void seed(Campaign campaign) {
        campaignRepository.save(campaign);
        issueGate.initialize(campaign);
    }
}
