package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductDto;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.product.ProductService;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분산 캐시(Redis) 환경의 데이터 정합성 통합 테스트
 * Cache-Aside 패턴에서의 데이터 조회(Put) 및 데이터 변경 시 무효화(Evict) 라이프사이클을 검증합니다.
 */
@SpringBootTest
public class ProductCacheIntegrationTest extends MariaDbRedisIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CacheManager cacheManager; // RedisCacheManager 주입

    // Redis는 전역 상태를 공유하는 외부 인프라이므로, 전체 테스트 실행 시
    // 테스트 간 간섭(Isolation 위반)을 방지하기 위해 매 테스트 전 캐시를 명시적으로 초기화합니다.
    @BeforeEach
    void clearCache(){
        Cache cache = cacheManager.getCache("products");
        if(cache != null) cache.clear();
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("캐시 정합성 검증: 조회(Miss) -> 캐시생성(Put) -> 수정(Evict) -> 재조회(New Put)")
    void verifyCacheConsistency() {
        // Given: 데이터 준비
        Product product = productRepository.save(new Product("Cache Test Item", 10000, 100));
        Long productId = product.getId();

        // When: 1차 조회 (Cache Miss -> DB 조회 및 Cache Put 발생)
        System.out.println("\n-> [1차 조회] 캐시 Miss -> DB 조회");
        productService.getProductById(productId);

        // Then: 캐시 등록 검증
        Cache cache = cacheManager.getCache("products");
        assertThat(cache).isNotNull();
        assertThat(cache.get(productId)).isNotNull();
        System.out.println(" 캐시 등록 확인 (Key: " + productId + ")");

        // When: 데이터 수정 (@CacheEvict 동작으로 인한 캐시 무효화 발생)
        System.out.println("\n-> [데이터 수정] 가격 20,000원으로 변경 -> @CacheEvict 발동");
        productService.updateProductPrice(productId, 20000);

        // Then: 캐시 삭제(Evict) 검증을 통한 정합성 확인
        assertThat(cache.get(productId)).isNull();
        System.out.println(" 캐시 삭제(Evict) 확인 -> 정합성 OK");

        // When: 2차 조회 (Cache Miss -> DB에서 최신 데이터 조회 및 Cache Put 발생)
        System.out.println("\n-> [2차 조회] 캐시 Miss -> DB에서 최신 값(20,000원) 조회");
        ProductDto.Response response = productService.getProductById(productId);

        // Then: 최신 데이터 반환 및 캐시 갱신(Refresh) 검증
        assertThat(response.getPrice()).isEqualTo(20000);
        System.out.println(" 최종 데이터 확인: " + response.getPrice() + "원");

        assertThat(cache.get(productId)).isNotNull();
        System.out.println(" 캐시 갱신(Refresh) 완료\n");
    }
}
