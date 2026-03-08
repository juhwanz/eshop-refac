package com.project.eshop_refact.event;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductCacheEventListener {

    // 트랜잭션 커밋이 성공적으로 완료된 직후에만 실행됨 (Stale Data 방지)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(value = "products", key = "#event.productId")
    public void handleCacheEvict(ProductCacheEvictEvent event) {
        // 별도의 구현 로직 없이 @CacheEvict 어노테이션을 동작시키는 트리거 역할
    }
}