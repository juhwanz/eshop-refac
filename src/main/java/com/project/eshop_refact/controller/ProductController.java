package com.project.eshop_refact.controller;

import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public Long registerProduct(@RequestBody @Valid ProductDto.RegisterRequest requestDto){
        return productService.registerProduct(requestDto);
    }

    @GetMapping("/{productId}")
    public ProductDto.Response getProductById(@PathVariable Long productId) {
        return productService.getProductById(productId);
    }

    /**
     * Legacy Offset Paging API
     */
    @GetMapping("/search")
    public Page<ProductDto.Response> searchProducts(
            ProductDto.SearchCondition condition,
            @PageableDefault(size = 10) Pageable pageable
    ){
        return productService.search(condition, pageable);
    }

    /**
     * [추가] No-Offset Paging API (성능 최적화 버전)
     * 사용 예: GET /api/products/search/no-offset?lastProductId=123&size=10
     */
    @GetMapping("/search/no-offset")
    public Slice<ProductDto.Response> searchNoOffset(
            @RequestParam(required = false) Long lastProductId,
            ProductDto.SearchCondition condition,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return productService.searchNoOffset(lastProductId, condition, pageable);
    }
}
