package com.junsoo.coupon.global.exception;

import java.util.Map;

public record ErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {
    public ErrorResponse(int status, String code, String message) {
        this(status, code, message, null);
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.getCode(), errorCode.getDefaultMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.getCode(), message);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.getCode(), message, fieldErrors);
    }
}
