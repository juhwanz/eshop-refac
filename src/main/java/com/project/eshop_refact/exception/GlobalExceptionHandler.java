package com.project.eshop_refact.exception;

import com.project.eshop_refact.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 비즈니스 로직 예외
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e){
        log.warn("Business Exception : {}", e.getMessage());

        ErrorCode errorCode = e.getErrorCode();

        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getHttpStatus());
    }

    // 2. 유효성 검사 예외 (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e){
        log.warn("Validation Exception : {}", e.getMessage());

        // 첫 번째 에러 메시지 추출 (Null Safety 적용)
        String message = (e.getBindingResult().getFieldError() != null) ?
                e.getBindingResult().getFieldError().getDefaultMessage() : "잘못된 요청입니다.";

        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        return new ResponseEntity<>(ErrorResponse.of(errorCode,message), errorCode.getHttpStatus());
    }

    // 3. DB 무결성 예외 (Unique Constraint 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("Data Integrity Violation : {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.DUPLICATE_RESOURCE;

        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getHttpStatus());
    }

    // 4. 잘못된 인자 예외 처리 (도메인 계층 등)
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal Argument Exception : {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        // 도메인에서 던진 구체적인 메시지를 그대로 전달
        return new ResponseEntity<>(ErrorResponse.of(errorCode, e.getMessage()), errorCode.getHttpStatus());
    }

    // 5. 잘못된 상태 예외 처리 (파사드 계층 등)
    @ExceptionHandler(IllegalStateException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        log.error("Illegal State Exception : {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return new ResponseEntity<>(ErrorResponse.of(errorCode, e.getMessage()), errorCode.getHttpStatus());
    }

    // 4. 나머지 모든 예외 처리 (최상위 Exception)
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Internal Server Error : ", e); // 서버 내부 에러는 error 레벨로 전체 스택 트레이스 로깅

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getHttpStatus());
    }
}