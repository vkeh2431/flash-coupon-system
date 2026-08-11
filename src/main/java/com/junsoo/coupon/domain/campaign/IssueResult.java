package com.junsoo.coupon.domain.campaign;

// issue.lua가 돌려주는 판정 결과. 상수 이름이 스크립트의 반환 문자열과 1:1로 대응한다.
public enum IssueResult {
    OK,
    NOT_FOUND,
    NOT_OPENED,
    CLOSED,
    PAUSED,
    ALREADY_ISSUED,
    OUT_OF_STOCK
}
