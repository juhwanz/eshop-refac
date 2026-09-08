package com.project.eshop_refact.domain.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class QueueProperties {

    private final Duration activeTtl;
    private final long promotionSizePerProduct;
    private final long productScanLimit;

    public QueueProperties(
            @Value("${app.queue.active-ttl:10m}") Duration activeTtl,
            @Value("${app.queue.promotion-size-per-product:100}") long promotionSizePerProduct,
            @Value("${app.queue.product-scan-limit:100}") long productScanLimit
    ) {
        if (activeTtl.isNegative() || activeTtl.isZero()) {
            throw new IllegalArgumentException("대기열 활성 권한 TTL은 0보다 커야 합니다.");
        }
        if (promotionSizePerProduct <= 0 || productScanLimit <= 0) {
            throw new IllegalArgumentException("대기열 처리 크기는 0보다 커야 합니다.");
        }
        this.activeTtl = activeTtl;
        this.promotionSizePerProduct = promotionSizePerProduct;
        this.productScanLimit = productScanLimit;
    }

    public Duration getActiveTtl() {
        return activeTtl;
    }

    public long getPromotionSizePerProduct() {
        return promotionSizePerProduct;
    }

    public long getProductScanLimit() {
        return productScanLimit;
    }
}
