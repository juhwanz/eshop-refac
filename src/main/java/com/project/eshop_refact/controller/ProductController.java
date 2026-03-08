package com.project.eshop_refact.controller;

import com.project.eshop_refact.dto.ApiResponse;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    // 상품 등록: @Valid를 통한 입력값 검증 및 Fail-Fast 적용
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> registerProduct(@RequestBody @Valid ProductDto.RegisterRequest requestDto){
        Long productId = productService.registerProduct(requestDto);
        return ResponseEntity.status(201).body(ApiResponse.success("상품등록 성공", productId));
    }

    @GetMapping("/{productId}")
    public ProductDto.Response getProductById(@PathVariable Long productId) {
        return productService.getProductById(productId);
    }

    // Offset Paging: 전통적인 게시판 형태의 페이지네이션 (Count Query 발생)
    @GetMapping("/search")
    public Page<ProductDto.Response> searchProducts(
            ProductDto.SearchCondition condition,
            @PageableDefault(size = 10) Pageable pageable
    ){
        return productService.search(condition, pageable);
    }

    // No-Offset Paging: 대용량 데이터 조회 성능 최적화 (Count Query 제거, 인덱스 활용)
    // Infinite Scroll 구현에 적합한 Slice 반환 타입 채택
    @GetMapping("/search/no-offset")
    public Slice<ProductDto.Response> searchNoOffset(
            @RequestParam(required = false) Long lastProductId,
            ProductDto.SearchCondition condition,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return productService.searchNoOffset(lastProductId, condition, pageable);
    }

    // 가격 수정 API
    @PatchMapping("/{productId}/price")
    public ResponseEntity<ApiResponse<ProductDto.Response>> updateProductPrice(
            @PathVariable Long productId,
            @RequestParam int newPrice) {
        ProductDto.Response response = productService.updateProductPrice(productId, newPrice);
        return ResponseEntity.ok(ApiResponse.success("가격수정 성공", response));
    }
}
