package com.project.eshop_refact.service.strategy;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class GeneralStockStrategy implements StockStrategy{

    private final ProductService productService;

    @Override
    public Product decrease(Long productId, int quantity){
        return productService.decreaseStockWithoutLock(productId, quantity);
    }
}

