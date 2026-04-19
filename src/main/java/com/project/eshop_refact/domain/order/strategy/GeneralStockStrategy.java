package com.project.eshop_refact.domain.order.strategy;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 일반 상품 재고 차감 전략
 * 동시성 충돌 빈도가 비교적 낮은 일반 상품에 적용되는 기본 구현체입니다.
 */
@Primary
@Component
@RequiredArgsConstructor
public class GeneralStockStrategy implements StockStrategy{

    private final ProductService productService;

    // 명시적 락(Redis/비관적) 오버헤드를 피하고, DB 수준의 격리에 의존하여 재고를 차감합니다.
    @Override
    public Product decrease(Long productId, int quantity){
        return productService.decreaseStockWithoutLock(productId, quantity);
    }
}

