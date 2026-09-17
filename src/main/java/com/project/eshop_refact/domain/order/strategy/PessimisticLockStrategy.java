package com.project.eshop_refact.domain.order.strategy;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DB 비관적 락(SELECT ... FOR UPDATE) 기반 재고 차감 전략.
 * Redisson 분산 락을 도입하기로 결정한 근거였던 비교 대조군으로, 실제 운영 경로에서는
 * 선택되지 않습니다({@link GeneralStockStrategy}만 {@code @Primary}이고 별도 Qualifier가 없음).
 * <p>
 * 이 클래스를 {@code @Qualifier}나 설정값으로 다시 활성화해도 유효한 비교가 되지 않습니다 —
 * 실제 주문 경로는 항상 {@link com.project.eshop_refact.domain.order.RedissonLockStockFacade}가
 * 먼저 상품별 Redis 락을 잡은 뒤에만 {@link StockStrategy#decrease}를 호출하므로, 이미 Redis 락으로
 * 직렬화된 상태에서 DB 락을 얹는 것일 뿐 DB 락 단독 시나리오를 재현하지 못합니다.
 * DB 락과 Redis 락의 실제 비교는 Redis 경로를 완전히 우회해 DB 락만 단독으로 재현하는
 * {@code OrderAvailabilityIntegrationTest}에서 수행합니다.
 */
@Component
@RequiredArgsConstructor
public class PessimisticLockStrategy implements StockStrategy {

    private final ProductService productService;

    @Override
    public Product decrease(Long productId, int quantity) {
        return productService.decreaseStock(productId, quantity);
    }
}
