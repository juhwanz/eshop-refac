package com.project.eshop_refact.domain.user;

/**
 * [사용자 권한 관리]
 * - 권한 값을 Enum -> 문자열 오타 및 유효하지 않은 값 할당을 컴파일 타임에 방지(Type Safety)
 * - 향후 권한 추가 시 변경 포인트 한곡으로 집중.
 */
public enum UserRoleEnum {
    USER,
    ADMIN
}

