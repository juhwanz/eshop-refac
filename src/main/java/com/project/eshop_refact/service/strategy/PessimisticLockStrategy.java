package com.project.eshop_refact.service.strategy;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PessimisticLockStrategy implements StockStrategy {

    private final ProductService productService;

    // S충돌이 빈번한 환경에서 완벽한 데이터 정합성 보장 (SELECT ... FOR UPDATE)
    // 락 대기 시간으로 인한 DB Connection Pool 점유율 상승 및 장애 전파 가능성 존재
    @Override
    public Product decrease(Long productId, int quantity) {
        return productService.decreaseStock(productId, quantity);
    }
}
