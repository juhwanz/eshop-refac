package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.product.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 1. connection-timeout=200ms (0.2초 안에 커넥션 못 얻으면 에러)
// 2. test.simulation.delay-ms=500ms (트랜잭션 하나당 0.5초 걸림) -> 즉, 커넥션 꽉 참
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.connection-timeout=250",
        "test.simulation.delay-ms=150",
        "app.order.lock.wait-time=30" //테스트 타임아웃 방지를 위해 대기 시간을 30초로 연장
})
@ActiveProfiles("test") //test 프로필 활성화하여 TestLatencyAspect 동작 유도
public class OrderAvailabilityIntegrationTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private RedissonLockStockFacade redissonLockStockFacade;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("서버 생존 테스트: 주문 폭주 시 DB락과 Redis락 비교")
    void compareAvailability() throws InterruptedException {
        // 1. [Before] DB 비관적 락 -> 5개 커넥션이 0.5초씩 점유 -> 조회 요청 0.2초 타임아웃 발생
        System.out.println("\n========== [1. DB 비관적 락 테스트 시작] ==========");
        TestResult dbResult = runTest("DB 비관적 락", (id, pid) -> productService.decreaseStock(pid, 1));

        //검증 : 비관적 락 정합성 OK(50개), 커넥션 고갈로 조회 실패
        assertThat(dbResult.remainingStock()).isGreaterThan(50);
        assertThat(dbResult.viewFailCount()).isGreaterThan(0);

        tearDown();

        // 2. [After] Redis 분산 락 -> Redis 대기열 -> DB 점유는 순차적 -> 커넥션 풀 여유 -> 조회 성공
        System.out.println("\n========== [2. Redis 분산 락 테스트 시작] ==========");
        TestResult redisResult = runTest("Redis 분산 락", (id, pid) -> redissonLockStockFacade.order(id, pid, 1));

        // 검증 : Redis 분산 락 정합성, 조회 OK
        assertThat(redisResult.remainingStock()).isBetween(50,55);
        assertThat(redisResult.viewFailCount()).isEqualTo(0);
    }

    record TestResult(int remainingStock, int viewFailCount) {
    }

    private TestResult runTest(String method, StockStrategy strategy) throws InterruptedException {
        int orderCount = 50;
        int viewCount = 20;

        Product product = productRepository.save(new Product("Hot Deal", 10000, 100));
        User user = userRepository.save(new User("tester", "1234", "name", UserRoleEnum.USER));

        ExecutorService executor = Executors.newFixedThreadPool(orderCount + viewCount);
        CountDownLatch latch = new CountDownLatch(orderCount + viewCount);

        AtomicInteger viewSuccess = new AtomicInteger(0);
        AtomicInteger viewFail = new AtomicInteger(0);

        long start = System.currentTimeMillis();

        // 1. 주문 폭주 (50명)
        for (int i = 0; i < orderCount; i++) {
            executor.submit(() -> {
                try {
                    strategy.decrease(user.getId(), product.getId());
                } catch (Exception e) {
                    // 주문 실패 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        // 2. 단순 조회 (20명)
        for (int i = 0; i < viewCount; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(100); // 주문들이 먼저 진입할 시간
                    productRepository.findById(product.getId());
                    viewSuccess.getAndIncrement();
                } catch (Exception e) {
                    viewFail.getAndIncrement(); // 커넥션 타임아웃!
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long time = System.currentTimeMillis() - start;

        Product finalProduct = productRepository.findById(product.getId()).orElseThrow();
        int finalStock = finalProduct.getStockQuantity();

        System.out.println("  [" + method + " 결과]");
        System.out.println("   - 총 소요 시간: " + time + "ms");
        System.out.println("   - 조회 성공: " + viewSuccess.get());
        System.out.println("   - 조회 실패: " + viewFail.get());

        return new TestResult(finalStock, viewFail.get());
    }

    @FunctionalInterface
    interface StockStrategy {
        void decrease(Long userId, Long productId);
    }
}