package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.repository.ProductRepository;
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


// JPA - saveAll() 대신 JDBC Batch Update를 사용해, 100만 건을 단 10초 이내로 떄려 박음.
// saveAll 은 몇 분 걸림.
@SpringBootTest
public class ProductDeepPaginationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
// 테스트 전에 50만 건 채워 넣기 (H2 DB - drop이라 바로 삭제 되기에)
    void setupData() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM products", Integer.class);
        if (count != null && count >= 500000) {
            return; // 이미 있으면 패스
        }

        System.out.println("데이터 준비 중@@@@@@@@@@@@@@2...");
        int batchSize = 1000;
        int totalCount = 500_000; // 50만 건 (100만 건은 H2에서 너무 오래 걸릴 수 있어 50만으로 조정)
        List<Object[]> batchArgs = new ArrayList<>();

        for (int i = 1; i <= totalCount; i++) {
            batchArgs.add(new Object[]{"Product_" + i, (i % 100) * 1000, 100});
            if (i % batchSize == 0) {
                // 주의: 테이블명이 'product' (엔티티명 기준)
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
        productRepository.search(new ProductDto.SearchCondition(), PageRequest.of(0, 10));
        System.out.println("진짜 속도 측정합니다.\n");
        // 1. 첫 페이지 조회 (Page 0)
        long start1 = System.currentTimeMillis();
        Page<Product> page1 = productRepository.search(
                new ProductDto.SearchCondition(),
                PageRequest.of(0, 10)
        );
        long end1 = System.currentTimeMillis();
        long time1 = end1 - start1;

        // 2. 깊은 페이지 조회 (Page 40,000 -> Offset 400,000)
        // OFFSET 40만: 앞에서부터 40만 개를 읽고 버려야 함 (병목 지점)
        long start2 = System.currentTimeMillis();
        Page<Product> pageLast = productRepository.search(
                new ProductDto.SearchCondition(),
                PageRequest.of(40000, 10)
        );
        long end2 = System.currentTimeMillis();
        long time2 = end2 - start2;

        // 결과 출력
        System.out.println(" [공정한 성능 비교 결과 (데이터 50만 건)]");
        System.out.println(" 1. 첫 페이지 (Offset 0)   : " + time1 + "ms");
        System.out.println(" 2. 끝 페이지 (Offset 40만): " + time2 + "ms");

        double diff = (double) time2 / (time1 == 0 ? 1 : time1);
        System.out.println(" -> 성능 차이: 약 " + String.format("%.1f", diff) + "배 느림");
    }

    @Test
    @DisplayName("OffSet vs No-Offset 검증 및 성능 비교")
    void compareSet(){
        // 1. 초기화 비용 제거
        productRepository.searchNoOffset(null, new ProductDto.SearchCondition(), PageRequest.of(0,10));

        // 2. [기존 방식] 끝 페이지 (Offset 40만)
        long startOld = System.currentTimeMillis();

        productRepository.search(
                new ProductDto.SearchCondition(),
                PageRequest.of(40000, 10) // 40,000 페이지 * 10 = 400,000
        );

        long endOld = System.currentTimeMillis();
        long timeOld = endOld - startOld;

        // 3. [개선 방식] 끝 페이지 (No-Offset)
        // 가정: ID가 순차적이라고 할 때, 40만 번째 ID는 대략 100,000 (총 50만개 중 뒤에서 조회한다고 가정)
        // No-Offset은 "어디를 조회하든" 속도가 똑같아야 함.
        // 테스트를 위해 적당한 중간 ID값을 lastProductId로 넘김 (예: 100,000)
        long startNew = System.currentTimeMillis();

        Slice<Product> resultNew = productRepository.searchNoOffset(
                100000L, // 직전 페이지의 마지막 ID라고 가정 (인덱스 탐색 시작점)
                new ProductDto.SearchCondition(),
                PageRequest.of(0, 10) // offset은 항상 0
        );

        long endNew = System.currentTimeMillis();
        long timeNew = endNew - startNew;

        // 결과 출력
        System.out.println(" [Offset vs No-Offset 성능 차이]");
        System.out.println(" 1. 기존 Offset (40만 건 스캔): " + timeOld + "ms");
        System.out.println(" 2. 개선 No-Offset (인덱스): " + timeNew + "ms");
        System.out.println(" -> 성능 개선: 약 " + (timeOld / (double)(timeNew == 0 ? 1 : timeNew)) + "배 빨라짐");

       // 데이터 정합성 판단
        assertThat(resultNew.getContent()).isNotEmpty();

        // 결과 데이터 개수 정상적으로 가져오는지 검증
        assertThat(resultNew.getContent()).isNotEmpty();
    }
}
