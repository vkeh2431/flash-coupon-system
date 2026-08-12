package com.junsoo.coupon.global.ratelimit;

import com.junsoo.coupon.global.exception.ErrorCode;
import com.junsoo.coupon.global.exception.ErrorResponse;
import com.junsoo.coupon.global.security.AuthUser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// JWT 필터 뒤에 놓는다. userId는 토큰을 검증한 뒤에야 생긴다.
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String ISSUE_PATTERN = "/campaigns/{campaignId}/coupons";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 이 필터는 보안 체인과 함께 웹 서버 기동 시점에 만들어진다. 그때 RateLimiter를 직접 물면
    // Redis 연결이 그 시점으로 당겨진다 — 필요한 건 첫 요청 때부터다.
    private final ObjectProvider<RateLimiter> rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Counter allowed;
    private final Counter blocked;

    public RateLimitFilter(ObjectProvider<RateLimiter> rateLimiter,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.allowed = counter(meterRegistry, "allowed");
        this.blocked = counter(meterRegistry, "blocked");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !pathMatcher.match(ISSUE_PATTERN, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = currentUserId();
        if (userId == null) {
            // 셀 키가 없다. 체인 끝의 AuthorizationFilter가 401로 거른다.
            chain.doFilter(request, response);
            return;
        }

        if (rateLimiter.getObject().tryAcquire(bucket(request, userId))) {
            allowed.increment();
            chain.doFilter(request, response);
            return;
        }
        blocked.increment();
        reject(response);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return null;
        }
        return user.getUserId();
    }

    // 숫자가 아닌 경로는 400으로 끝나지만 거기까지 가는 비용은 그대로 든다. 그렇다고 값을
    // 그대로 키에 쓰면 값마다 바구니가 생겨 제한이 무력해지므로, 전부 한 바구니로 뭉쳐 담는다.
    private String bucket(HttpServletRequest request, Long userId) {
        String raw = pathMatcher.extractUriTemplateVariables(ISSUE_PATTERN, request.getRequestURI())
                .get("campaignId");
        try {
            return "issue:" + Long.parseLong(raw) + ":" + userId;
        } catch (NumberFormatException e) {
            return "issue:invalid:" + userId;
        }
    }

    // Retry-After가 없으면 클라이언트가 즉시 재시도해 방어가 부하를 되레 늘린다.
    private void reject(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.TOO_MANY_REQUESTS;
        response.setStatus(errorCode.getStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(properties.windowSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }

    private static Counter counter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("coupon.ratelimit.result").tag("result", result).register(meterRegistry);
    }
}
