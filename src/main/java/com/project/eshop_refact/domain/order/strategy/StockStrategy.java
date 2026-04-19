package com.project.eshop_refact.domain.order.strategy;

import com.project.eshop_refact.domain.product.Product;

/**
 * 재고 차감 전략 인터페이스
 * 상품 특성 및 동시성 환경에 맞춘 다양한 차감 알고리즘을 유연하게 교체하기 위해 사용합니다.
 */
public interface StockStrategy {
    Product decrease(Long productId, int quantity);
}
