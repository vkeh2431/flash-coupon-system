package com.junsoo.coupon.global.security;

import com.junsoo.coupon.domain.user.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * JWT 클레임만으로 구성되는 인증 주체. DB를 조회하지 않는다.
 *
 * 로그인 경로의 CustomUserDetails와 분리한 이유:
 * 로그인은 비밀번호 대조가 필요해 DB 조회가 필수지만, 이미 발급된 토큰을 검증하는 경로는
 * userId와 role만 있으면 되고 그 둘은 토큰 안에 들어 있다.
 * 한 클래스를 공유하면 필터 경로에서 email·password가 null이 되어 나중에 터진다.
 *
 * 측정 근거: 필터가 요청마다 user를 조회하던 구조에서 인증 경로 처리량이
 * 940 req/s(MySQL CPU 96%)에 묶였다. 인증 없는 엔드포인트는 5,000 req/s였다.
 * Redis·Kafka를 넣어도 이 조회가 남으면 DB 왕복이 0이 되지 않아 개선이 드러나지 않는다.
 */
@Getter
public class AuthUser {

    private final Long userId;
    private final Role role;

    public AuthUser(Long userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
