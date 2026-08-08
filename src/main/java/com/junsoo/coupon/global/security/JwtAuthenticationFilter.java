package com.junsoo.coupon.global.security;

import com.junsoo.coupon.domain.user.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            Claims claims = jwtProvider.parseOrNull(token);
            if (claims != null && jwtProvider.isAccessToken(claims)) {
                Long userId = jwtProvider.getUserId(claims);
                Role role = jwtProvider.getRole(claims);
                if (userId != null && role != null) {
                    // DB를 조회하지 않는다. 근거와 감수한 리스크는 AuthUser 참고.
                    AuthUser principal = new AuthUser(userId, role);
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    log.debug("JWT 클레임이 불완전해 미인증으로 진행: userId={}, role={}", userId, role);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }

}
