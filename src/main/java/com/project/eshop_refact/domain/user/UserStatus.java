package com.project.eshop_refact.domain.user;

/**
 * 사용자 계정 상태
 */
public enum UserStatus {
    ACTIVE,     // 정상 활성화
    LOCKED,     // 보안 정책(인증 실패 누적 등) 및 관리자에 의한 계정 잠금
    DELETED     // 탈퇴 처리된 계정 (논리적 삭제)
}

