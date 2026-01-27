package com.project.eshop_refact.exception;

import com.project.eshop_refact.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
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
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("Valdiation Error : {}", message);

        ErrorResponse response = ErrorResponse.builder()
                .code("INVALID_INPUT")
                .message(message)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
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