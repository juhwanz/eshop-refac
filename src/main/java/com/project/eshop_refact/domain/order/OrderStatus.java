package com.project.eshop_refact.domain.order;

// Type-Safety: 컴파일 시점에 유효하지 않은 상태 값(Invalid State) 할당 차단
// Maintenance: 비즈니스 로직에서 사용하는 상태 값의 중앙 관리(Centralized Management)
public enum OrderStatus {
    ORDER,  // 주문 완료.
    CANCEL, // 주문 실패
    COMPLETED
}

