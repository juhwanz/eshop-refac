package com.project.eshop_refact.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductCacheEvictEvent {
    private Long productId;
}
