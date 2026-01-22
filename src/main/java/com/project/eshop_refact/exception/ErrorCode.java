package com.project.eshop_refact.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    EMAIL_DUPLICATION(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다."),
    PASSWORD_NOT_MATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "가입되지 않은 이메일입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 상품을 찾을 수 없습니다."),

    OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "재고가 부족합니다");
    private final HttpStatus httpStatus;
    private final String message;
}
