package com.junsoo.coupon.loadtest;

import com.junsoo.coupon.domain.user.Role;
import com.junsoo.coupon.global.security.JwtProperties;
import com.junsoo.coupon.global.security.JwtProvider;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 실행: ./gradlew seedLoadTest [-Dusers=30000]
 */
public final class LoadTestSeeder {

    private static final String EMAIL_FORMAT = "load-%06d@test.local";
    private static final String EMAIL_PATTERN = "load-%@test.local";
    private static final int BATCH_SIZE = 1_000;

    public static void main(String[] args) throws Exception {
        int userCount = Integer.getInteger("users", 30_000);
        String url = System.getProperty("db.url",
                "jdbc:mysql://localhost:3306/coupon?rewriteBatchedStatements=true");
        String username = System.getProperty("db.username", "coupon");
        String password = System.getProperty("db.password", "coupon");
        Path out = Path.of(System.getProperty("out", "k6/tokens.txt"));

        // 앱의 application.properties 기본값과 같아야 한다. 다르면 k6가 전부 401을 받는다.
        String secret = System.getProperty("jwt.secret",
                "change-me-in-prod-this-is-a-very-long-dev-secret-key-0123456789");
        long validityMs = Duration.ofDays(Long.getLong("token.validity.days", 30)).toMillis();

        long startedAt = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            insertUsers(conn, userCount);
            List<Long> userIds = loadUserIds(conn);

            if (userIds.size() != userCount) {
                throw new IllegalStateException(
                        "유저 수가 맞지 않는다: 요청 %d, 실제 %d. 이전 측정에서 남은 유저가 있으면 지우고 다시 실행할 것"
                                .formatted(userCount, userIds.size()));
            }

            writeTokens(out, userIds, secret, validityMs);
        }

        System.out.printf("완료: 유저 %,d명 · 토큰 %s (%.1f초)%n",
                userCount, out.toAbsolutePath(), (System.currentTimeMillis() - startedAt) / 1000.0);
    }

    // INSERT IGNORE — 이미 시딩된 상태에서 다시 돌려도 유저 id가 바뀌지 않는다.
    // id가 바뀌면 이전에 발급된 쿠폰과 대응이 끊긴다.
    private static void insertUsers(Connection conn, int userCount) throws Exception {
        String sql = "INSERT IGNORE INTO user (email, password, role, created_at) VALUES (?, ?, 'USER', NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= userCount; i++) {
                ps.setString(1, EMAIL_FORMAT.formatted(i));
                ps.setString(2, "not-used");    // 로그인 경로를 안 타므로 인코딩하지 않는다
                ps.addBatch();
                if (i % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        System.out.printf("유저 %,d명 확보%n", userCount);
    }

    private static List<Long> loadUserIds(Connection conn) throws Exception {
        String sql = "SELECT id FROM user WHERE email LIKE ? ORDER BY id";
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, EMAIL_PATTERN);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    // 앱의 JwtProvider를 그대로 쓴다 — 클레임 구조가 어긋나면 401이 나는데 원인 찾기가 어렵다.
    private static void writeTokens(Path out, List<Long> userIds, String secret, long validityMs) throws Exception {
        JwtProvider jwtProvider = new JwtProvider(new JwtProperties(secret, validityMs, validityMs));

        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (Long userId : userIds) {
                // newLine()은 Windows에서 \r\n을 쓴다. \r이 남으면 HTTP 헤더 값으로 못 쓴다.
                writer.write(jwtProvider.createAccessToken(userId, Role.USER));
                writer.write('\n');
            }
        }
        System.out.printf("토큰 %,d개 생성 (만료 %d일)%n", userIds.size(), Duration.ofMillis(validityMs).toDays());
    }
}
