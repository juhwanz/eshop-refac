package com.project.eshop_refact.domain.user;

public enum UserStatus {
    ACTIVE, // 정상 활성화
    LOCKED, // 비밀번호 5회 이상 틀림 or 정지
    DELETED // 회원 탈퇴
}

