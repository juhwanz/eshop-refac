package com.project.eshop_refact.global.common;

import com.project.eshop_refact.global.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;

/**
 * 공통 에러 응답 객체
 * 클라이언트가 예측 가능한 일관된 에러 포맷(상태 코드, 에러 코드, 메시지)을 제공하여,
 * 프론트엔드의 전역 예외 처리 로직 구현을 돕습니다.
 */
@Getter
@Builder
public class ErrorResponse {

    private final int status;
    private final String code;
    private final String message;

    /**
     * 사전 정의된 에러 코드(ErrorCode) 기반의 기본 에러 응답을 생성합니다.
     */
    public static ErrorResponse of(ErrorCode errorCode){
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .build();
    }

    /**
     * @Valid 검증 실패와 같이, 동적으로 생성된 커스텀 에러 메시지가 필요한 경우 사용합니다.
     */
    public static ErrorResponse of(ErrorCode errorCode, String customMessage){
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .code(errorCode.name())
                .message(customMessage)
                .build();
    }

    /**
     * Spring Security 필터(Filter) 등 전역 예외 처리기(@RestControllerAdvice)를
     * 거치지 않는 계층에서 직접 에러 응답을 구성하여 반환할 때 사용합니다.
     */
    public static ErrorResponse of(int status, String code, String message) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .build();
    }
}

