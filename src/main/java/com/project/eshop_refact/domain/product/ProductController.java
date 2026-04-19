package com.project.eshop_refact.domain.product;

import com.project.eshop_refact.global.common.ApiResponse;
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

    /**
     * 상품 등록 API
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> registerProduct(@RequestBody @Valid ProductDto.RegisterRequest requestDto){
        Long productId = productService.registerProduct(requestDto);
        return ResponseEntity.status(201).body(ApiResponse.success("상품등록 성공", productId));
    }

    /**
     * 상품 단건 조회 API
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDto.Response>> getProductById(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("상품 조회 성공", productService.getProductById(productId)));
    }

    /**
     * 상품 검색 API (Offset Paging)
     * 전통적인 게시판 형태의 페이지네이션을 지원합니다.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProductDto.Response>>> searchProducts(
            @ModelAttribute ProductDto.SearchCondition condition,
            @PageableDefault(size = 10) Pageable pageable
    ){
        return ResponseEntity.ok(ApiResponse.success("상품 검색 성공",productService.search(condition,pageable)));
    }

    /**
     * 상품 무한 스크롤 검색 API (No-Offset Paging)
     * 대용량 데이터 조회 시 발생하는 카운트(Count) 쿼리 병목을 제거하고,
     * 클러스터링 인덱스(PK)를 활용하여 조회 성능을 획기적으로 최적화합니다.
     */
    @GetMapping("/search/no-offset")
    public ResponseEntity<ApiResponse<Slice<ProductDto.Response>>> searchNoOffset(
            @RequestParam(required = false) Long lastProductId,
            ProductDto.SearchCondition condition,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("상품 스크롤 검색 성공", productService.searchNoOffset(lastProductId, condition, pageable)));
    }

    /**
     * 상품 가격 수정 API
     */
    @PatchMapping("/{productId}/price")
    public ResponseEntity<ApiResponse<ProductDto.Response>> updateProductPrice(
            @PathVariable Long productId,
            @RequestBody @Valid ProductDto.PriceUpdateRequest request) {
        ProductDto.Response response = productService.updateProductPrice(productId, request.getNewPrice());
        return ResponseEntity.ok(ApiResponse.success("가격수정 성공", response));
    }
}
