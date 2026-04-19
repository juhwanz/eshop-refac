package com.project.eshop_refact.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 공통 API 응답 객체(Wrapper)
 * 프론트엔드 클라이언트가 일관된 형태로 데이터를 파싱할 수 있도록 모든 API의 성공 응답 규격을 통일합니다.
 */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private String message;
    private T data;

    /**
     * 데이터 없이 상태 메시지만 반환하는 성공 응답
     */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(message, null);
    }

    /**
     * 상태 메시지와 결과 데이터를 함께 반환하는 성공 응답
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, data);
    }

    /**
     * 기본 메시지와 결과 데이터를 반환하는 성공 응답
     */
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>("요청이 성공적으로 처리되었습니다.", data); }
}
