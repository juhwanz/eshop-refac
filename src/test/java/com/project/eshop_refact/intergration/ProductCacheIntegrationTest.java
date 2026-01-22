package com.project.eshop_refact.intergration;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.repository.ProductRepository;
import com.project.eshop_refact.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ProductCacheIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CacheManager cacheManager; // RedisCacheManager 주입

    @Test
    @DisplayName("캐시 정합성 검증: 조회(Miss) -> 캐시생성(Put) -> 수정(Evict) -> 재조회(New Put)")
    void verifyCacheConsistency() {
        // 1. [Given] 데이터 준비 (초기 가격 10,000원)
        Product product = productRepository.save(new Product("Cache Test Item", 10000, 100));
        Long productId = product.getId();

        // 2. [When] 1차 조회 (캐시가 없으니 DB에서 읽어옴)
        System.out.println("\n-> [1차 조회] 캐시 Miss -> DB 조회");
        productService.getProductById(productId);

        // 3. [Then] 캐시가 생성되었는지 확인
        Cache cache = cacheManager.getCache("products");
        assertThat(cache).isNotNull();
        // Redis에 저장된 값 확인 (JSON 형태일 것임)
        assertThat(cache.get(productId)).isNotNull();
        System.out.println(" 캐시 등록 확인 (Key: " + productId + ")");

        // 4. [When] 가격 수정 (10,000원 -> 20,000원)
        // @CacheEvict가 동작해서 캐시를 지워야 함!
        System.out.println("\n-> [데이터 수정] 가격 20,000원으로 변경 -> @CacheEvict 발동");
        productService.updateProductPrice(productId, 20000);

        // 5. [Then] 캐시가 정말 지워졌는지 확인 (Evict 검증)
        // 주의: Redis에서 키가 사라져야 정합성이 유지됨 (옛날 값 10,000원이 남아있으면 안 됨)
        assertThat(cache.get(productId)).isNull();
        System.out.println(" 캐시 삭제(Evict) 확인 -> 정합성 OK");

        // 6. [When] 2차 조회 (캐시가 없으니 다시 DB에서 20,000원을 읽어와야 함)
        System.out.println("\n-> [2차 조회] 캐시 Miss -> DB에서 최신 값(20,000원) 조회");
        ProductDto.Response response = productService.getProductById(productId);

        // 7. [Then] 조회된 값이 20,000원인지 확인
        assertThat(response.getPrice()).isEqualTo(20000);
        System.out.println(" 최종 데이터 확인: " + response.getPrice() + "원");

        // 8. [Then] 캐시가 다시 갱신되었는지 확인
        assertThat(cache.get(productId)).isNotNull();
        System.out.println(" 캐시 갱신(Refresh) 완료\n");
    }
}