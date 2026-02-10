package com.project.eshop_refact.exception;

import com.project.eshop_refact.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 로직 예외
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e){
        log.warn("Business Exception : {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode);
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    // 유효성 검사 예외
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e){
        // 첫 번쨰 에러 메시지만.
        // Null Safety 적용
        String message = (e.getBindingResult().getFieldError() != null) ?
                e.getBindingResult().getFieldError().getDefaultMessage() : "잘못된 요청입니다.";

        ErrorResponse response = ErrorResponse.builder()
                .code("INVALID_INPUT")
                .message(message)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 3. [추가] DB 무결성 예외 (Unique Constraint 등)
    // 회원가입 레이스 컨디션 발생 시 중복 에러를 409로 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("Data Integrity Violation : {}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .code("DUPLICATE_RESOURCE")
                .message("이미 존재하는 데이터입니다.") // 예: 이미 가입된 이메일
                .build();

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // 나머지 예외
    @ExceptionHandler
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Internal Server Error : ", e);// 서버에는 상세 로그 남김 (Stack Trace 포함)


        ErrorResponse response = ErrorResponse
                .builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("서버 내부 오류가 발생했습니다 ")
                .build();

        return new ResponseEntity<>(response, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}