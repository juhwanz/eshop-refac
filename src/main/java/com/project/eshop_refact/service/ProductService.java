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
@Transactional(readOnly = true) // 기본 읽기 전용 : 불필요한 Dirty Checking 비용 제거 및 리소스 최적화
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional  // 쓰기 트랜잭션 - Atomicity: 상품 등록의 원자성 보장
    public Long registerProduct(ProductDto.RegisterRequest requestDto){
        Product product = new Product(
                requestDto.getName(),
                requestDto.getPrice(),
                requestDto.getStockQuantity()
        );

        Product savedProduct = productRepository.save(product);

        return savedProduct.getId();
    }

    // Caching Strategy: Look-aside Pattern 적용 (Read-Through)
    // Traffic Offloading: 빈번한 조회 요청(Hot Data)에 대한 DB 부하 분산
    @Cacheable(value = "products", key = "#productId", cacheManager = "cacheManager")
    public ProductDto.Response getProductById(Long productId) {
        Product product =  productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return new ProductDto.Response(product);
    }

    // Legacy Strategy: Offset Pagination (Deep Pagination 시 성능 저하 이슈 존재 - 모니터링 용도 유지)
    public Page<ProductDto.Response> search(ProductDto.SearchCondition condition, Pageable pageable){
        return productRepository.search(condition, pageable)
                .map(ProductDto.Response::new);
    }

    //No-Offset (Cursor-based) Pagination
    // Scalability: 데이터 증가와 무관하게 일정한 조회 속도(O(1))를 보장하는 인덱스 스캔 방식
    public Slice<ProductDto.Response> searchNoOffset(Long lastProductId, ProductDto.SearchCondition condition, Pageable pageable) {
        return productRepository.searchNoOffset(lastProductId, condition, pageable)
                .map(ProductDto.Response::new);
    }

    /**
     * [Concurrency Control: Pessimistic Lock]
     * - Strong Consistency: 데이터 정합성 최우선 보장 (Select ... for Update)
     * - Cache Invalidation: 데이터 변경 시 즉시 캐시(Evict)를 날려 Stale Data 조회 방지
     */
    @Transactional  // 쓰기
    @CacheEvict(value = "products", key = "#productId") // 데이터 변화 -> 캐시 삭제
    public Product decreaseStock(Long productId, int quantity) {
        // Redisson Lock이 앞단에서 동시성을 제어하므로, 여기서는 DB 락 없이 일반 조회 후 차감 가능
        // (단, 안전을 위해 버전 관리(@Version)나 DB 제약조건을 병행하는 것이 실무적임)
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.removeStock(quantity);
        return product;
    }

    /**
     * [Distributed Lock Support Implementation]
     * - Separation of Concerns: 락 획득/해제 책임은 Facade(Redisson)에 위임하고, 본 메서드는 비즈니스 로직(차감)에 집중
     * - Propagation: 상위 Facade 트랜잭션에 참여
     */
    @Transactional  // 쓰기
    @CacheEvict(value = "products", key = "#productId")
    public Product decreaseStockWithoutLock(Long productId, int quantity){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        product.removeStock(quantity);

        return product;
    }

    // Data Consistency: 가격 수정 시 캐시 정합성을 위해 Evict 수행
    @Transactional  // 쓰기
    @CacheEvict(value = "products", key = "#productId")
    public ProductDto.Response updateProductPrice(Long productId, int newPrice) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.updatePrice(newPrice);
        return new ProductDto.Response(product);
    }
}

