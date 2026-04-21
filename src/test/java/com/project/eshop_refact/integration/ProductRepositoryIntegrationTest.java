package com.project.eshop_refact.integration;

import com.project.eshop_refact.global.config.QueryDslConfig;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductDto;
import com.project.eshop_refact.domain.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductRepository 영속성 계층 슬라이스 테스트
 * QueryDSL을 활용한 복합 조건 동적 쿼리와 No-Offset(Cursor) 기반 페이징 로직의 정합성을 검증합니다.
 */
@DataJpaTest
@Import(QueryDslConfig.class) // QueryDSL JPAQueryFactory 빈 주입을 위한 설정 로드
public class ProductRepositoryIntegrationTest {

    @Autowired
    ProductRepository productRepository;

    @Test
    @DisplayName("QueryDSL 동적 쿼리: 이름과 가격 범위로 검색")
    void searchTest() {
        // given
        Product p1 = new Product("LG Notebook", 1500000, 10);
        Product p2 = new Product("Samsung Notebook", 2000000, 10);
        Product p3 = new Product("Apple Mac", 3000000, 10);
        Product p4 = new Product("Mouse", 50000, 100);

        productRepository.save(p1);
        productRepository.save(p2);
        productRepository.save(p3);
        productRepository.save(p4);

        // 검색 조건: 이름에 "Notebook"이 포함되고, 가격이 1,000,000원 이상인 데이터
        ProductDto.SearchCondition condition = new ProductDto.SearchCondition();
        condition.setName("Notebook");
        condition.setMinPrice(1000000);

        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Product> result = productRepository.search(condition, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(2); // LG, Samsung 2개
        assertThat(result.getContent()).extracting("name")
                .containsExactlyInAnyOrder("LG Notebook", "Samsung Notebook");

        // 페이징 메타데이터(전체 레코드 수) 정합성 검증
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("QueryDSL 검색: 조건이 없을 경우 전체 조회")
    void searchAllTest() {
        // given
        productRepository.save(new Product("A", 1000, 1));
        productRepository.save(new Product("B", 2000, 1));

        ProductDto.SearchCondition condition = new ProductDto.SearchCondition(); // 빈 조건
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Product> result = productRepository.search(condition, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("QueryDSL No - offset 페이징 : lastProductId 보다 작은 데이터 순차 조회")
    void searchNoOffsetTest(){
        // given
        Product p1 = productRepository.save(new Product("Item1", 1000, 10));
        Product p2 = productRepository.save(new Product("Item2", 2000, 10));
        Product p3 = productRepository.save(new Product("Item3", 3000, 10));
        Product p4 = productRepository.save(new Product("Item4", 4000, 10));

        ProductDto.SearchCondition condition = new ProductDto.SearchCondition();
        PageRequest pageRequest = PageRequest.of(0, 2);

        // No-Offset 페이징을 위해 이전 페이지의 마지막 데이터 ID를 커서(Cursor)로 설정
        Long lastProductId = p4.getId();

        // when
        // 설정된 커서(lastProductId) 미만의 데이터를 내림차순(DESC)으로 조회 (ID 3, 2 기대)
        Slice<Product> result = productRepository.searchNoOffset(lastProductId, condition, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting("name")
                .containsExactly("Item3", "Item2");

        // Slice 인터페이스의 hasNext()를 통해 다음 페이지(Item1) 존재 여부 검증
        assertThat(result.hasNext()).isTrue();
    }
}