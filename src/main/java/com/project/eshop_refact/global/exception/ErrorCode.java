package com.project.eshop_refact.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 1. 공통 에러 (Global)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."), // @Valid 검증 실패 시
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."), // 예상치 못한 에러
    // 락 획득 실패 (503 Service Unavailable)
    LOCK_ACQUISITION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "현재 요청이 많아 처리가 지연되고 있습니다."),
    // DB 및 리소스 중복 에러 (409 Conflict)
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),

    // 2. 유저 관련 에러 (User)
    EMAIL_DUPLICATION(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    LOGIN_FAILED(HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호가 일치하지 않습니다."), // 보안상 모호하게 주는 경우도 있음
    // 계정 상태 관련 에러
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "비밀번호 5회 오류 또는 관리자에 의해 정지된 계정입니다."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "탈퇴된 계정입니다."),

    // 3. 인증/인가 에러 (Auth)
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."), // 401: 로그인이 필요함
    FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),    // 403: 로그인 했으나 권한이 부족함 (관리자 페이지 등)

    // 4. 상품 관련 에러 (Product)
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 상품을 찾을 수 없습니다."),
    OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "재고가 부족합니다."),

    // 5. 주문 관련 에러 (Order)
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 주문을 찾을 수 없습니다."),
    CANNOT_CANCEL_ORDER(HttpStatus.BAD_REQUEST, "이미 배송이 완료되어 주문을 취소할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
