package com.project.eshop_refact.controller;

import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @GetMapping("/search")
    public Page<ProductDto.Response> searchProducts(
            ProductDto.SearchCondition condition,
            @PageableDefault(size = 10) Pageable pageable
    ){
        return productService.search(condition, pageable);
    }
}
