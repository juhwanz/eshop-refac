package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.order.OrderService;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.domain.order.strategy.PessimisticLockStrategy;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * 대규모 동시성 트래픽 통합 테스트 및 락 메커니즘 성능 분석
 * 1. 초과 요청(경합) 발생 시의 재고 정합성(Concurrency Limit) 검증
 * 2. DB 비관적 락(Pessimistic)과 Redis 분산 락(Redisson) 간의 처리 시간 및 안정성 트레이드오프 비교
 */
@SpringBootTest(properties = {
        // 실제 운영 환경과 유사한 락 대기 상황 연출을 위해 타임아웃 설정
        "spring.datasource.hikari.maximum-pool-size=50",
        "spring.datasource.hikari.connection-timeout=5000",
        "spring.jpa.properties.hibernate.show_sql=false",
        "app.order.lock.wait-time=120",
        "logging.level.root=error"
})
public class OrderConcurrencyIntegrationTest extends MariaDbRedisIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private RedissonLockStockFacade redissonLockStockFacade;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    @Autowired private PessimisticLockStrategy pessimisticLockStrategy;
    @Autowired private RedissonClient redissonClient;

    // 락 성능 검증에 집중하기 위해 대기열 서비스는 통과하도록 Mocking
    @MockBean private WaitingQueueService waitingQueueService;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("[정합성 검증] 40개 재고 45명 동시 주문 -> 40개 성공, 5개 실패.")
    void verifyConcurrencyLimit() throws InterruptedException {
        // Given
        int stockQuantity = 40;
        int threadCount = 45;

        given(waitingQueueService.isAllowed(anyLong())).willReturn(true);

        Product product = productRepository.save(new Product("Hot Deal Item", 10000, stockQuantity));
        Long productId = product.getId();

        for (int i = 0; i < threadCount; i++) {
            userRepository.save(new User("user" + i + "@test.com", "1234", "user" + i, UserRoleEnum.USER));
        }

        // 스레드 풀 진입 전 엔티티 리스트를 캐싱하여 병목 지점(Lock) 외의 DB 커넥션 경합 방지
        List<User> users = userRepository.findAll();

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    User user = users.get(index);
                    redissonLockStockFacade.order(user.getId(), productId, 1);
                    successCount.getAndIncrement();
                } catch (Exception e) {
                    failCount.getAndIncrement();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(2, TimeUnit.MINUTES)).isTrue();
        executorService.shutdownNow();

        // Then
        Product updatedProduct = productRepository.findById(productId).orElseThrow();

        System.out.println("\n[정합성 검증 결과]");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패: " + failCount.get());
        System.out.println("남은 재고: " + updatedProduct.getStockQuantity());

        assertThat(successCount.get()).isEqualTo(40);
        assertThat(failCount.get()).isEqualTo(5);
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("[순수 락 성능 비교] DB Pessimistic Vs Redis (1:1)")
    void comparePureLockPerformance() throws InterruptedException {
        tearDown();
        long dbLockTime = testConcurrency(
                "DB 비관적 락",
                (userId, productId) -> pessimisticLockStrategy.decrease(productId, 1)
        );

        tearDown();
        long redisLockTime = testConcurrency(
                "Redis 분산 락",
                (userId, productId) -> {
                    RLock lock = redissonClient.getLock("test:perf:pure:" + productId);
                    try{
                        boolean available = lock.tryLock(30, -1, TimeUnit.SECONDS);
                        if(available){
                            pessimisticLockStrategy.decrease(productId,1);
                        }
                    } catch(InterruptedException e){
                        Thread.currentThread().interrupt();
                    } finally {
                        if(lock.isHeldByCurrentThread()){
                            lock.unlock();
                        }
                    }
                }
        );

        System.out.println("\n [성능 비교 결과 리포트]");
        System.out.println(" - DB 비관적 락 소요 시간: " + dbLockTime + "ms");
        System.out.println(" - Redis 분산 락 소요 시간: " + redisLockTime + "ms");
        System.out.println(" - 결과: " + calculateDiff(dbLockTime, redisLockTime));
    }

    @Test
    @DisplayName("[아키텍쳐 성능 비교] DB 비관적 락 Vs Redis 분산 락")
    void compareArchitecturePerformance() throws InterruptedException{
        tearDown();

        long dbLockTime = testConcurrency(
                "DB Pessimistic Lock",
                (userId, productId) -> pessimisticLockStrategy.decrease(productId, 1)
        );

        tearDown();

        long redisLockTime = testConcurrency(
                "Redis Lock",
                // 분산 환경의 가용성을 위해 트랜잭션 커밋 범위를 확장한 Facade 로직 수행
                (userId, productId) -> redissonLockStockFacade.order(userId, productId, 1)
        );

        System.out.println("\n [ 아키텍처 성능 비교 (전체 주문 로직)]");
        System.out.println(" - DB 락 (단순 차감): " + dbLockTime + "ms");
        System.out.println(" - Redis 락 (전체 주문): " + redisLockTime + "ms");
        System.out.println(" - 결과: " + calculateDiff(dbLockTime, redisLockTime) + " (트랜잭션 범위 확장에 따른 Trade-off)");
        System.out.println("=============================================\n");

    }
    // 중복 코드를 제거한 테스트 실행기
    private long testConcurrency(String testName, OrderTask task) throws InterruptedException {
        // Given
        int stockQuantity = 100;
        int threadCount = 100;

        Product product = productRepository.save(new Product("Test Item", 10000, stockQuantity));
        User user = userRepository.save(new User("tester@test.com", "1234", "tester", UserRoleEnum.USER));

        given(waitingQueueService.isAllowed(anyLong())).willReturn(true);

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    task.run(user.getId(), product.getId());
                } catch (Exception e) {
                    // 성능 측정 오차를 줄이기 위해 예외 로깅 생략
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(2, TimeUnit.MINUTES)).isTrue();
        executorService.shutdownNow();
        return System.currentTimeMillis() - startTime;
    }

    @FunctionalInterface
    interface OrderTask {
        void run(Long userId, Long productId);
    }

    private String calculateDiff(long db, long redis) {
        if (db == 0) return "0%";
        double diff = ((double) (redis - db) / db) * 100;

        if (diff > 0) {
            return String.format("Redis가 DB보다 약 %.1f배 느림 (안정성 확보를 위한 Trade-off)", (redis / (double) db));
        } else {
            return String.format("Redis가 %.2f%% 더 빠름", Math.abs(diff));
        }
    }
}
