package com.project.eshop_refact.integration;

import com.project.eshop_refact.config.QueryDslConfig;
import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest // JPA 관련 컴포넌트만 로드 (가볍고 빠름)
@Import(QueryDslConfig.class) // QueryDSL 설정 클래스 로드 필수!
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

        // 검색 조건: 이름에 "Noteboook"이 포함되고, 가격이 100만원 이상인 것
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

        // 추가 검증: Paging Total Count
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
}