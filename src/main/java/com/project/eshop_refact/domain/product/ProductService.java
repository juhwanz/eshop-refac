package com.project.eshop_refact.domain.product;


import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 도메인 서비스
 * 클래스 상단에 읽기 전용 트랜잭션을 적용하여 불필요한 스냅샷 생성 및 Dirty Checking 비용을 제거해 조회 성능을 최적화했습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional  // 쓰기 트랜잭션
    public Long registerProduct(ProductDto.RegisterRequest requestDto){
        Product product = new Product(
                requestDto.getName(),
                requestDto.getPrice(),
                requestDto.getStockQuantity()
        );

        Product savedProduct = productRepository.save(product);

        return savedProduct.getId();
    }

    /**
     * 상품 단건 조회
     * 빈번한 조회 요청(Hot Data)에 대해 DB 부하를 분산시키기 위해 Look-aside 캐싱을 적용합니다.
     */
    @Cacheable(value = "products", key = "#productId", cacheManager = "cacheManager")
    public ProductDto.Response getProductById(Long productId) {
        Product product =  productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return new ProductDto.Response(product);
    }

    /**
     * 상품 검색 (Offset 기반)
     * 깊은 페이지 조회 시 성능 저하가 발생할 수 있으므로, 관리자 페이지 등 제한적인 요구사항에 사용합니다.
     */
    public Page<ProductDto.Response> search(ProductDto.SearchCondition condition, Pageable pageable){
        return productRepository.search(condition, pageable)
                .map(ProductDto.Response::new);
    }

    /**
     * 상품 무한 스크롤 검색 (No-Offset 기반)
     * 데이터 증가량과 무관하게 일정한 조회 속도를 보장하기 위해 인덱스 스캔 방식을 사용합니다.
     */
    public Slice<ProductDto.Response> searchNoOffset(Long lastProductId, ProductDto.SearchCondition condition, Pageable pageable) {
        return productRepository.searchNoOffset(lastProductId, condition, pageable)
                .map(ProductDto.Response::new);
    }


    /**
     * 비관적 락(Pessimistic Lock) 기반 재고 차감
     * 트랜잭션 롤백 시 발생할 수 있는 캐시 정합성 문제를 방지하기 위해,
     * @CacheEvict 대신 트랜잭션 커밋 성공 시점에만 동작하는 이벤트를 발행합니다.
     */
    @Transactional  // 쓰기
    public Product decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.removeStock(quantity);
        eventPublisher.publishEvent(new ProductCacheEvictEvent(productId));
        return product;
    }


    /**
     * 분산 락(Distributed Lock) 기반 재고 차감
     * 동시성 제어 락 획득/해제 책임은 외부 Facade 계층(Redisson)에 위임하고,
     * 본 메서드는 순수 비즈니스 로직(재고 차감)에만 집중하도록 설계했습니다.
     */
    @Transactional  // 쓰기
    public Product decreaseStockWithoutLock(Long productId, int quantity){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.removeStock(quantity);
        eventPublisher.publishEvent(new ProductCacheEvictEvent(productId));
        return product;
    }

    /**
     * 상품 가격 업데이트
     */
    @Transactional  // 쓰기
    // @CacheEvict(value = "products", key = "#productId") // 롤백 위험이 있는 기존 방식 제거
    public ProductDto.Response updateProductPrice(Long productId, int newPrice) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        product.updatePrice(newPrice);
        eventPublisher.publishEvent(new ProductCacheEvictEvent(productId));

        return new ProductDto.Response(product);
    }
}

