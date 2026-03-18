package com.project.eshop_refact.dto;

import com.project.eshop_refact.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;

// API Consistency: 클라이언트에게 일관된 에러 응답 포맷을 제공하여 예외 처리 로직 통일
@Getter
@Builder
public class ErrorResponse {

    private final int status;
    private final String code;
    private final String message;

    // Static Factory Method: 객체 생성 로직을 캡슐화하고 메서드 명을 통해 가독성 향상
    public static ErrorResponse of(ErrorCode errorCode){
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .build();
    }

    // 오버로딩 메서드 : 유효성 검사 등 커스텀 메시지 필요시.
    public static ErrorResponse of(ErrorCode errorCode, String customMessage){
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .code(errorCode.name())
                .message(customMessage)
                .build();
    }

    // Spring Security 필터 등 ErrorCode Enum을 쓰기 어려운 곳에서 직접 생성할 때 사용
    public static ErrorResponse of(int status, String code, String message) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .build();
    }
}

