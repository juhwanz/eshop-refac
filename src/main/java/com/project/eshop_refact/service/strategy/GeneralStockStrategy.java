package com.project.eshop_refact.service.strategy;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// Strategy Pattern: 런타임 시점에 상품 특성(일반 vs 이벤트)에 따라 재고 차감 알고리즘 교체
// Default Strategy: 동시성 충돌 빈도가 낮은 일반 상품(99%)을 위한 기본 구현체
@Component
@Primary
@RequiredArgsConstructor
public class GeneralStockStrategy implements StockStrategy{

    private final ProductService productService;

    // Latency Optimization: 명시적 락(Redis/Pessimistic) 오버헤드를 제거하여 응답 속도 극대화
    // ACID: DB 자체의 원자성(Atomicity)과 격리 수준(Isolation Level)에 의존하여 처리
    @Override
    public Product decrease(Long productId, int quantity){
        return productService.decreaseStockWithoutLock(productId, quantity);
    }
}

