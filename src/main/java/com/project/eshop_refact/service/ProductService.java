package com.project.eshop_refact.service;


import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import com.project.eshop_refact.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;


    @Transactional
    public Long registerProduct(ProductDto.RegisterRequest requestDto){
        Product product = new Product(
                requestDto.getName(),
                requestDto.getPrice(),
                requestDto.getStockQuantity()
        );

        Product savedProduct = productRepository.save(product);

        return savedProduct.getId();
    }

    @Cacheable(value = "products", key = "#productId", cacheManager = "cacheManager")
    @Transactional(readOnly = true)
    public ProductDto.Response getProductById(Long productId) {
        Product product =  productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return new ProductDto.Response(product);
    }

    /**
     * Legacy: Offset 기반 페이징 (기존 방식)
     */
    @Transactional(readOnly = true)
    public Page<ProductDto.Response> search(ProductDto.SearchCondition condition, Pageable pageable){
        return productRepository.search(condition, pageable)
                .map(ProductDto.Response::new);
    }

    /**
     * Optimized: No-Offset 기반 페이징 (성능 최적화)
     * [추가] Repository의 searchNoOffset을 호출하고 DTO로 변환
     */
    @Transactional(readOnly = true)
    public Slice<ProductDto.Response> searchNoOffset(Long lastProductId, ProductDto.SearchCondition condition, Pageable pageable) {
        return productRepository.searchNoOffset(lastProductId, condition, pageable)
                .map(ProductDto.Response::new);
    }

    /**
     * DB Pessimistic Lock(비관적 락)을 이용한 재고 감소
     * - 동시성 제어 비교 테스트를 위한 Legacy 메서드
     */
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public Product decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.removeStock(quantity);
        return product;
    }

    /*
        Reids 분삭 락(Facade) 내부에서 Tx 범위로 감싸서 호출함. -> DB 락을 걸지 않음
     */
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public Product decreaseStockWithoutLock(Long productId, int quantity){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        product.removeStock(quantity);

        return product;
    }

    /**
     * 상품 가격 수정
     * Cache Evict: 데이터 정합성을 위해 수정 시 캐시 제거
     */
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public ProductDto.Response updateProductPrice(Long productId, int newPrice) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.updatePrice(newPrice);
        return new ProductDto.Response(product);
    }
}

