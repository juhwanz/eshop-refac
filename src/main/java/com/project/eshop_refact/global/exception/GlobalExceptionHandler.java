package com.project.eshop_refact.global.exception;

import com.project.eshop_refact.global.common.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리기
 * 애플리케이션 전반에서 발생하는 예외를 가로채어, 클라이언트에게 일관된 형식(ErrorResponse)의 에러 응답을 반환합니다.
 * 이를 통해 컨트롤러 계층의 예외 처리 중복 코드를 제거하고 핵심 비즈니스 로직에 집중할 수 있도록 돕습니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 커스텀 비즈니스 예외 처리
     * 예측 가능한 클라이언트의 잘못된 요청이므로 WARN 레벨로 로깅합니다.
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e){
        log.warn("Business Exception : {}", e.getMessage());

        ErrorCode errorCode = e.getErrorCode();

        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getHttpStatus());
    }

    /**
     * DTO 유효성 검증 실패 예외 처리
     * @Valid 어노테이션에 의해 발생하는 에러 메시지 중 첫 번째를 추출하여 반환합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e){
        log.warn("Validation Exception : {}", e.getMessage());

        // 첫 번째 에러 메시지 추출 (Null Safety 적용)
        String message = (e.getBindingResult().getFieldError() != null) ?
                e.getBindingResult().getFieldError().getDefaultMessage() : "잘못된 요청입니다.";

        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        return new ResponseEntity<>(ErrorResponse.of(errorCode,message), errorCode.getHttpStatus());
    }

    /**
     * 데이터베이스 무결성 위반 예외 처리
     * 유니크 제약조건(이메일 중복 등) 위반 시 발생합니다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("Data Integrity Violation : {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.DUPLICATE_RESOURCE;

        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getHttpStatus());
    }

    /**
     * 잘못된 인자 값 예외 처리
     * 도메인 계층 등에서 던져진 구체적인 예외 메시지를 그대로 클라이언트에게 전달합니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal Argument Exception : {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        return new ResponseEntity<>(ErrorResponse.of(errorCode, e.getMessage()), errorCode.getHttpStatus());
    }

    /**
     * 객체 상태 위반 예외 처리
     */
    @ExceptionHandler(IllegalStateException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        log.error("Illegal State Exception : {}", e.getMessage());
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return new ResponseEntity<>(ErrorResponse.of(errorCode, e.getMessage()), errorCode.getHttpStatus());
    }

    /**
     * 최상위 예외 처리 (Catch-all)
     * 위에서 핸들링하지 못한 예측 불가능한 서버 내부 오류를 처리합니다.
     * 장애 원인 파악을 위해 전체 스택 트레이스를 ERROR 레벨로 남깁니다.
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Internal Server Error : ", e); // 서버 내부 에러는 error 레벨로 전체 스택 트레이스 로깅

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getHttpStatus());
    }
}