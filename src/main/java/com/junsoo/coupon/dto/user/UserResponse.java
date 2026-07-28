package com.junsoo.coupon.dto.user;

import com.junsoo.coupon.domain.user.Role;
import com.junsoo.coupon.domain.user.User;

public record UserResponse(
        Long id,
        String email,
        Role role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}
