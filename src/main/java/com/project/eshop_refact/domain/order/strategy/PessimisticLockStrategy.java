package com.project.eshop_refact.domain.order.strategy;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class PessimisticLockStrategy implements StockStrategy {

    private final ProductService productService;

    /**
     * 동시성 충돌이 빈번한 환경을 위한 비관적 락 기반 재고 차감 전략
     * 데이터 정합성을 강하게 유지하지만, 락 대기로 인한 DB 커넥션 풀 점유 및 병목 가능성을 고려해야 합니다.
     */
    @Override
    public Product decrease(Long productId, int quantity) {
        return productService.decreaseStock(productId, quantity);
    }
}
