package com.project.eshop_refact.domain.order.strategy;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class PessimisticLockStrategy implements StockStrategy {

    private final ProductService productService;

    // 충돌이 빈번한 환경에서 강한 정합성을 확보하는 방식 (SELECT ... FOR UPDATE)
    // 단, 락 대기로 인한 DB Connection Pool 점유율 상승 가능성 존재
    @Override
    public Product decrease(Long productId, int quantity) {
        return productService.decreaseStock(productId, quantity);
    }
}
