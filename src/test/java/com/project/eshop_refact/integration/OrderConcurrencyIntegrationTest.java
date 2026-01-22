package com.project.eshop_refact.integration;

import com.project.eshop_refact.facade.RedissonLockStockFacade;
import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.domain.User;
import com.project.eshop_refact.domain.UserRoleEnum;
import com.project.eshop_refact.repository.OrderRepository;
import com.project.eshop_refact.repository.ProductRepository;
import com.project.eshop_refact.repository.UserRepository;
import com.project.eshop_refact.service.OrderService;
import com.project.eshop_refact.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        // 실제 운영 환경과 유사한 락 대기 상황 연출을 위해 타임아웃 설정
        "spring.datasource.hikari.maximum-pool-size=50",
        "spring.datasource.hikari.connection-timeout=5000"
})
public class OrderConcurrencyIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private ProductService productService; // DB 락 테스트용
    @Autowired private RedissonLockStockFacade redissonLockStockFacade; // Redis 락 테스트용

    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName(" Deep Dive: DB 비관적 락 vs Redis 분산 락 성능 비교")
    void comparePerformance() throws InterruptedException {
        // =================================================
        // 1. [Before] DB 비관적 락 (Pessimistic Lock) 테스트
        // =================================================
        long dbLockTime = testConcurrency(
                "DB 비관적 락",
                (userId, productId) -> productService.decreaseStock(productId, 1) // 기존 DB 락 메서드 호출
        );

        // =================================================
        // 2. [After] Redis 분산 락 (Distributed Lock) 테스트
        // =================================================
        // 데이터 초기화 (공정한 비교를 위해)
        tearDown();

        long redisLockTime = testConcurrency(
                "Redis 분산 락",
                (userId, productId) -> redissonLockStockFacade.order(userId, productId, 1)
        );

        // =================================================
        // 3. 결과 리포트 (이 부분을 캡처해서 포트폴리오에 넣으세요)
        // =================================================
        System.out.println("\n=============================================");
        System.out.println(" [성능 비교 결과 리포트]");
        System.out.println("1. DB 비관적 락 소요 시간: " + dbLockTime + "ms");
        System.out.println("2. Redis 분산 락 소요 시간: " + redisLockTime + "ms");
        System.out.println(" 성능 개선율: " + calculateImprovement(dbLockTime, redisLockTime) + "% 단축");
        System.out.println("=============================================\n");
    }

    // 중복 코드를 제거한 테스트 실행기
    private long testConcurrency(String testName, StockStrategy strategy) throws InterruptedException {
        // Given
        int stockQuantity = 100;
        int threadCount = 100; // 100명 동시 요청

        Product product = productRepository.save(new Product("Test Item", 10000, stockQuantity));
        User user = userRepository.save(new User("tester@test.com", "1234", "tester", UserRoleEnum.USER));

        ExecutorService executorService = Executors.newFixedThreadPool(32); // 쓰레드 풀 제한
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    strategy.decrease(user.getId(), product.getId());
                } catch (Exception e) {
                    System.out.println(testName + " 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 검증 (재고가 0이어야 함)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, updatedProduct.getStockQuantity(), testName + " 재고 불일치 발생!");

        System.out.println( testName + " 완료 (" + duration + "ms)");
        return duration;
    }

    // 람다식을 위한 함수형 인터페이스
    @FunctionalInterface
    interface StockStrategy {
        void decrease(Long userId, Long productId);
    }

    private String calculateImprovement(long before, long after) {
        if (before == 0) return "0";
        double improvement = ((double) (before - after) / before) * 100;
        return String.format("%.2f", improvement);
    }
}