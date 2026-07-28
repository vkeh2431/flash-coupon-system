package com.junsoo.coupon.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "사용자명은 필수입니다.")
        @Size(min=8, max=100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.")
        String password
) {
}
