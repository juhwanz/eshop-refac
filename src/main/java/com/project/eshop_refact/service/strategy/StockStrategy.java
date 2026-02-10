package com.project.eshop_refact.service.strategy;

import com.project.eshop_refact.domain.Product;

// OCP (Open-Closed Principle): 비즈니스 로직 수정 없이 새로운 동시성 제어 전략(Redis, Kafka 등) 확장 가능
// Abstraction: 구체적인 락 구현체(Implementation)와의 결합도를 낮추어 유지보수성 향상 (Loose Coupling)
public interface StockStrategy {
    Product decrease(Long productId, int quantity);
}
