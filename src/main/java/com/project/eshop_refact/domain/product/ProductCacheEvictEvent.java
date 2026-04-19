package com.project.eshop_refact.domain.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 상품 캐시 무효화 이벤트 (Event Payload)
 * 핵심 비즈니스 로직(ProductService)과 캐시 제어 로직(인프라) 간의 강한 결합도를 낮추기 위해,
 * Spring ApplicationEventPublisher를 통해 발행되는 이벤트 객체입니다.
 */
@Getter
@AllArgsConstructor
public class ProductCacheEvictEvent {
    private Long productId;
}
