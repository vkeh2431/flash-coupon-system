package com.junsoo.coupon.global.exception;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MeterRegistry meterRegistry;

    // 선착순 이벤트에서는 거절이 성공보다 압도적으로 많아, warn으로 남기면
    // 초당 수천 줄이 쌓이면서 정작 봐야 할 신호가 묻힌다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.debug("비즈니스 예외: code={}, message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getDefaultMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach( error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("입력값 검증 실패: {}", fieldErrors);

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getDefaultMessage(), fieldErrors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    // 데드락·락 타임아웃은 여기서 끝낸다. 잡지 않으면 Tomcat이 건마다 스택트레이스를 찍는다.
    // 건수는 로그가 아니라 카운터로 센다 — v1에서 0이 되는지 대조해야 하는 값이다.
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException e) {
        String type = e.getClass().getSimpleName();
        meterRegistry.counter("coupon.db.errors", "type", type).increment();
        log.warn("DB 예외: {} — {}", type, e.getMostSpecificCause().getMessage());

        ErrorCode errorCode = ErrorCode.DB_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }
}
