package com.project.eshop_refact.exception;

import com.project.eshop_refact.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e){
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode);
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e){
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        ErrorResponse response = ErrorResponse.builder()
                .code("INVALID_INPUT")
                .message(message)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        // 운영 환경에서는 상세 로그 남기고, 반환은 일반적인 메시지 권장.
        ErrorResponse response = ErrorResponse
                .builder()
                .code("SERVER_ERROR")
                .message("서버 내부 오류가 발생했습니다: " + e.getMessage())
                .build();

        return new ResponseEntity<>(response, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}