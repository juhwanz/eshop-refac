package com.project.eshop_refact.service;


import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.event.ProductCacheEvictEvent;
import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import com.project.eshop_refact.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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


    /*
        [비관적 락 테스트 용] (Select ... for Update)
     */
    @Transactional  // 쓰기
    // @CacheEvict 삭제 -> 트랜잭션 커밋 전 캐시 삭제로 인한 동시성 이슈 방지
    public Product decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.removeStock(quantity);
        //트랜잭션 커밋 후 캐시를 지우도록 이벤트 발행
        eventPublisher.publishEvent(new ProductCacheEvictEvent(productId));
        return product;
    }


    /*
        [본 상품 갯수 감소 로직 - Redis Distributed Lock]
        - 락 획득/해제 책임은 Facade(Redisson)에 위임하고, 본 메서드는 비즈니스 로직(차감)에 집중
     */
    @Transactional  // 쓰기
    // @CacheEvict 삭제
    public Product decreaseStockWithoutLock(Long productId, int quantity){
        // Redisson Lock이 앞단에서 동시성을 제어하므로, 여기서는 DB 락 없이 일반 조회 후 차감 가능
        //(단, 안전을 위해 버전 관리(@Version)나 DB 제약조건을 병행하는 것이 실무적임)
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.removeStock(quantity);
        // 트랜잭션 커밋 후 캐시를 지우도록 이벤트 발행
        eventPublisher.publishEvent(new ProductCacheEvictEvent(productId));
        return product;
    }

    // Data Consistency: 가격 수정 시 캐시 정합성을 위해 Evict 수행
    // Data Consistency: 가격 수정 시 캐시 정합성을 위해 Event 발행으로 변경
    @Transactional  // 쓰기
    // @CacheEvict(value = "products", key = "#productId") // 롤백 위험이 있는 기존 방식 제거
    public ProductDto.Response updateProductPrice(Long productId, int newPrice) {
        // 보통 가격 업데이트는 관리자 또는 소수 -> 정합성 발생할 가능 적어서 락 미적용.
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        product.updatePrice(newPrice);
        // 트랜잭션 커밋이 성공적으로 완료된 직후(AFTER_COMMIT) 캐시 삭제
        eventPublisher.publishEvent(new ProductCacheEvictEvent(productId));

        return new ProductDto.Response(product);
    }
}

