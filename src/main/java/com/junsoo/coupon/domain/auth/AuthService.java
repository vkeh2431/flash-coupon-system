package com.junsoo.coupon.domain.auth;

import com.junsoo.coupon.domain.user.Role;
import com.junsoo.coupon.domain.user.User;
import com.junsoo.coupon.domain.user.UserRepository;
import com.junsoo.coupon.dto.auth.LoginRequest;
import com.junsoo.coupon.dto.auth.RefreshRequest;
import com.junsoo.coupon.dto.auth.SignupRequest;
import com.junsoo.coupon.dto.auth.TokenResponse;
import com.junsoo.coupon.dto.user.UserResponse;
import com.junsoo.coupon.global.exception.BusinessException;
import com.junsoo.coupon.global.exception.ErrorCode;
import com.junsoo.coupon.global.exception.ResourceNotFoundException;
import com.junsoo.coupon.global.security.CustomUserDetails;
import com.junsoo.coupon.global.security.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private static final String GRANT_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if(userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_IN_USE);
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()), Role.USER);
        userRepository.save(user);
        return UserResponse.from(user);
    }

    public TokenResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return issue(principal.getUserId(), principal.getRole());
    }

    public TokenResponse refresh(String refreshToken) {
        Claims claims = jwtProvider.parseOrNull(refreshToken);
        if (claims == null || !jwtProvider.isRefreshToken(claims)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long userId = jwtProvider.getUserId(claims);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 재발급은 드문 경로이므로 여기서는 DB에서 최신 role을 읽는다.
        // 발급 경로(JwtAuthenticationFilter)와 달리 요청당 비용이 문제되지 않는다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        return issue(user.getId(), user.getRole());
    }

    public TokenResponse issue(Long userId, Role role) {
        String accessToken = jwtProvider.createAccessToken(userId, role);
        String refreshToken = jwtProvider.createRefreshToken(userId);
        return new TokenResponse(accessToken,refreshToken, GRANT_TYPE);
    }
}
