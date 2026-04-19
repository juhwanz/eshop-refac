package com.project.eshop_refact.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 최상위 커스텀 예외
 * RuntimeException을 상속하여 선언적 트랜잭션(@Transactional) 환경에서 발생 시 자동으로 롤백을 유발하며,
 * 사전에 정의된 표준화된 에러 코드(ErrorCode)를 통해 전역 예외 처리기(GlobalExceptionHandler)로 예외를 전달합니다.
 */
@Getter
public class BusinessException extends RuntimeException{

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

