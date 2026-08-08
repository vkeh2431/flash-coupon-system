package com.junsoo.coupon.global.security;

import com.junsoo.coupon.domain.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = properties.accessTokenValidity();
        this.refreshTokenValidity = properties.refreshTokenValidity();
    }

    public String createAccessToken(Long userId, Role role) {
        return buildToken(userId, role.name(), TYPE_ACCESS, accessTokenValidity);
    }

    public String createRefreshToken(Long userId) {
        return buildToken(userId, null, TYPE_REFRESH, refreshTokenValidity);
    }

    private String buildToken(Long userId, String role, String type, long validityMs) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(validityMs)))
                .signWith(key);
        if(role != null) {
            builder.claim(CLAIM_ROLE, role);
        }
        return builder.compact();
    }


    // 서명 검증과 클레임 파싱을 한 번에 수행한다.
    public Claims parseOrNull(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public Long getUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Role getRole(Claims claims) {
        String role = claims.get(CLAIM_ROLE, String.class);
        if (role == null) {
            return null;
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
