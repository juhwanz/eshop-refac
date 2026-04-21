package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductDto;
import com.project.eshop_refact.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;


import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


/**
 * 대용량 데이터 환경의 페이징 성능(Deep Pagination) 및 최적화 통합 테스트
 * JPA saveAll()의 오버헤드를 우회하고 대규모 더미 데이터를 고속으로 적재하기 위해 JDBC Batch Update를 활용합니다.
 */
@SpringBootTest
public class ProductDeepPaginationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Deep Pagination 병목 시뮬레이션을 위한 50만 건의 테스트 데이터 적재
    @BeforeEach
    void setupData() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM products", Integer.class);
        if (count != null && count >= 500000) {
            return; // 이미 있으면 패스
        }

        System.out.println("데이터 적재 중...");
        int batchSize = 1000;
        int totalCount = 500_000;
        List<Object[]> batchArgs = new ArrayList<>();

        for (int i = 1; i <= totalCount; i++) {
            batchArgs.add(new Object[]{"Product_" + i, (i % 100) * 1000, 100});
            if (i % batchSize == 0) {
                jdbcTemplate.batchUpdate("INSERT INTO products (name, price, stock_quantity) VALUES (?, ?, ?)", batchArgs);
                batchArgs.clear();
            }
        }
        System.out.println(" 데이터 준비 완료!");
    }

    @Test
    @DisplayName("Deep 페이지 네이션 문제 : 첫 페이지 vs 마지막 페이지 속도 비교")
    void comparePage(){
        System.out.println("초기화 비용");
        // Warm-up: 클래스 로딩 및 초기 쿼리 파싱 비용을 제외하여 성능 측정 오차를 방지합니다.
        productRepository.search(new ProductDto.SearchCondition(), PageRequest.of(0, 10));
        System.out.println("진짜 속도 측정합니다.\n");

        // 1. 첫 페이지 조회 (빠른 응답 기대)
        long start1 = System.currentTimeMillis();
        Page<Product> page1 = productRepository.search(
                new ProductDto.SearchCondition(),
                PageRequest.of(0, 10)
        );
        long end1 = System.currentTimeMillis();
        long time1 = end1 - start1;

        // 2. 깊은 페이지(Deep Page) 조회 병목 시뮬레이션
        // 설정된 Offset(40만) 만큼 데이터를 디스크에서 순차적으로 읽고 버리는 DB 엔진의 병목 현상을 유도합니다.
        long start2 = System.currentTimeMillis();
        Page<Product> pageLast = productRepository.search(
                new ProductDto.SearchCondition(),
                PageRequest.of(40000, 10)
        );
        long end2 = System.currentTimeMillis();
        long time2 = end2 - start2;

        System.out.println(" [공정한 성능 비교 결과 (데이터 50만 건)]");
        System.out.println(" 1. 첫 페이지 (Offset 0)   : " + time1 + "ms");
        System.out.println(" 2. 끝 페이지 (Offset 40만): " + time2 + "ms");

        double diff = (double) time2 / (time1 == 0 ? 1 : time1);
        System.out.println(" -> 성능 차이: 약 " + String.format("%.1f", diff) + "배 느림");
    }

    @Test
    @DisplayName("OffSet vs No-Offset 검증 및 성능 비교")
    void compareSet(){
        // 1. Warm-up
        productRepository.searchNoOffset(null, new ProductDto.SearchCondition(), PageRequest.of(0,10));

        // 2. 기존 Offset 방식 (Deep Paging 병목 발생 구간)
        long startOld = System.currentTimeMillis();

        productRepository.search(
                new ProductDto.SearchCondition(),
                PageRequest.of(40000, 10) // 40,000 페이지 * 10 = 400,000
        );

        long endOld = System.currentTimeMillis();
        long timeOld = endOld - startOld;

        // 3. No-Offset (Cursor) 방식
        // 마지막으로 조회한 ID를 기준(인덱스 탐색 시작점)으로 사용하여, 읽고 버리는 Offset 연산을 회피합니다.
        long startNew = System.currentTimeMillis();

        Slice<Product> resultNew = productRepository.searchNoOffset(
                100000L,
                new ProductDto.SearchCondition(),
                PageRequest.of(0, 10)
        );

        long endNew = System.currentTimeMillis();
        long timeNew = endNew - startNew;

        System.out.println(" [Offset vs No-Offset 성능 차이]");
        System.out.println(" 1. 기존 Offset (40만 건 스캔): " + timeOld + "ms");
        System.out.println(" 2. 개선 No-Offset (인덱스): " + timeNew + "ms");
        System.out.println(" -> 성능 개선: 약 " + (timeOld / (double)(timeNew == 0 ? 1 : timeNew)) + "배 빨라짐");

        // 4. No-Offset 쿼리 실행 정합성 검증
        assertThat(resultNew.getContent()).isNotEmpty();
    }
}
