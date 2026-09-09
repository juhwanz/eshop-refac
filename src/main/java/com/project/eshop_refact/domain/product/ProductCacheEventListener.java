package com.project.eshop_refact.domain.product;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상품 캐시 무효화 이벤트 핸들러
 */
@Component
public class ProductCacheEventListener {

    /**
     * 데이터베이스 트랜잭션 커밋이 성공적으로 완료된 직후(AFTER_COMMIT)에 캐시를 무효화하여,
     * 롤백된 변경 때문에 캐시가 먼저 제거되는 것을 피합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(value = "products", key = "#event.productId")
    public void handleCacheEvict(ProductCacheEvictEvent event) {
    }
}
