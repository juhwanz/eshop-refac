package com.project.eshop_refact.service.strategy;

import com.project.eshop_refact.domain.Product;

public interface StockStrategy {
    Product decrease(Long productId, int quantity);
}
