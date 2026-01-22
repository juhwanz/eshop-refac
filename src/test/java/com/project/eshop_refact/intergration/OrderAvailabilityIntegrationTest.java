package com.project.eshop_refact.intergration;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.domain.User;
import com.project.eshop_refact.domain.UserRoleEnum;
import com.project.eshop_refact.facade.RedissonLockStockFacade;
import com.project.eshop_refact.repository.OrderRepository;
import com.project.eshop_refact.repository.ProductRepository;
import com.project.eshop_refact.repository.UserRepository;
import com.project.eshop_refact.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// [설정]
// 1. connection-timeout=200ms (0.2초 안에 커넥션 못 얻으면 에러)
// 2. test.simulation.delay-ms=500ms (트랜잭션 하나당 0.5초 걸림) -> 즉, 커넥션 꽉 참
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.connection-timeout=250",
        "test.simulation.delay-ms=500"
})
public class OrderAvailabilityIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private RedissonLockStockFacade redissonLockStockFacade;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        // 이제 타임아웃(200ms)이 넉넉해서 여기서 에러 안 남!
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("서버 생존 테스트: 주문 폭주 시 DB락은 죽고, Redis락은 산다")
    void compareAvailability() throws InterruptedException {
        // 1. [Before] DB 비관적 락 -> 5개 커넥션이 0.5초씩 점유 -> 조회 요청 0.2초 타임아웃 발생
        System.out.println("\n========== [1. DB 비관적 락 테스트 시작] ==========");
        runTest("DB 비관적 락", (id, pid) -> productService.decreaseStock(pid, 1));

        tearDown();

        // 2. [After] Redis 분산 락 -> Redis 대기열 -> DB 점유는 순차적 -> 커넥션 풀 여유 -> 조회 성공
        System.out.println("\n========== [2. Redis 분산 락 테스트 시작] ==========");
        runTest("Redis 분산 락", (id, pid) -> redissonLockStockFacade.order(id, pid, 1));
    }

    private void runTest(String method, StockStrategy strategy) throws InterruptedException {
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

        System.out.println("  [" + method + " 결과]");
        System.out.println("   - 총 소요 시간: " + time + "ms");
        System.out.println("   - 조회 성공: " + viewSuccess.get());
        System.out.println("   - 조회 실패: " + viewFail.get());

        if (viewFail.get() > 0) {
            System.out.println("    ➡️ 결과: 장애 발생 ❌ (DB 커넥션 고갈됨)");
        } else {
            System.out.println("    ➡️ 결과: 서비스 안정적 ✅ (Redis가 DB 보호함)");
        }
    }

    @FunctionalInterface
    interface StockStrategy {
        void decrease(Long userId, Long productId);
    }
}