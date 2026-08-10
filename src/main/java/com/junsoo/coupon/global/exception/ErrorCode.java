package com.junsoo.coupon.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값 검증에 실패했습니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다"),

    CAMPAIGN_NOT_OPENED(HttpStatus.CONFLICT, "CAMPAIGN_NOT_OPENED", "캠페인이 아직 열리지 않았습니다"),
    CAMPAIGN_CLOSED(HttpStatus.CONFLICT, "CAMPAIGN_CLOSED", "캠페인이 종료되었습니다"),
    CAMPAIGN_PAUSED(HttpStatus.CONFLICT, "CAMPAIGN_PAUSED", "캠페인이 중단되었습니다"),
    CAMPAIGN_OUT_OF_STOCK (HttpStatus.CONFLICT, "CAMPAIGN_OUT_OF_STOCK", "캠페인의 쿠폰이 전부 소진되었습니다"),
    ISSUE_LOCK_TIMEOUT(HttpStatus.CONFLICT, "ISSUE_LOCK_TIMEOUT", "요청이 몰려 발급하지 못했습니다. 다시 시도해 주세요"),

    COUPON_ALREADY_ISSUED (HttpStatus.CONFLICT, "COUPON_ALREADY_ISSUED", "캠페인의 쿠폰을 이미 발급받았습니다"),
    COUPON_ALREADY_REDEEMED(HttpStatus.CONFLICT, "COUPON_ALREADY_REDEEMED", "이미 사용한 쿠폰입니다"),
    COUPON_REVOKED(HttpStatus.CONFLICT, "COUPON_REVOKED", "사용할 수 없는 쿠폰입니다"),
    COUPON_EXPIRED(HttpStatus.CONFLICT, "COUPON_EXPIRED", "유효기간이 지난 쿠폰입니다"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 틀렸습니다"),
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", "해당 이메일은 이미 사용중입니다"),

    DB_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "DB_ERROR", "요청을 처리하지 못했습니다");



    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;
}
