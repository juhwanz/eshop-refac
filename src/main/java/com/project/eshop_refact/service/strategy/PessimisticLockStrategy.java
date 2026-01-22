package com.project.eshop_refact.service.strategy;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PessimisticLockStrategy implements StockStrategy {

    private final ProductService productService;

    /**
     * DB Pessimistic Lock(Select ... for update)을 이용한 재고 차감.
     * <p>
     * [장점] 데이터 정합성이 강력하게 보장됨.
     * [단점] 트래픽 급증 시 DB 커넥션 점유 시간이 길어져 '장애 전파(Cascading Failure)' 발생 위험 있음.
     * -> Redisson 분산 락 도입의 비교 대조군으로 사용됨.
     * </p>
     */
    @Override
    public Product decrease(Long productId, int quantity) {
        return productService.decreaseStock(productId, quantity);
    }
}
