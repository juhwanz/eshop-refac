package com.project.eshop_refact.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private String message;
    private T data;

    // Only Msg
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(message, null);
    }

    // Msg + Data
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, data);
    }

    // Only Data
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>("요청이 성공적으로 처리되었습니다.", data); }
}
