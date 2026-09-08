package com.project.eshop_refact.domain.user;

/**
 * 사용자 계정 상태
 */
public enum UserStatus {
    ACTIVE,     // 정상 활성화
    LOCKED,     // 기존 계정 잠금 상태. 로그인 실패에 따른 임시 잠금은 lockedUntil로 관리
    DELETED     // 탈퇴 처리된 계정 (논리적 삭제)
}
