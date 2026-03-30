package com.project.eshop_refact.global.exception;

import lombok.Getter;

// Unchecked Exception: @Transactional 환경에서 자동 롤백(Rollback)을 유발하여 데이터 정합성 보장
// Centralized Error Handling: 비즈니스 로직 상의 예외 상황을 표준화된 ErrorCode로 통합 관리
@Getter
public class BusinessException extends RuntimeException{

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

