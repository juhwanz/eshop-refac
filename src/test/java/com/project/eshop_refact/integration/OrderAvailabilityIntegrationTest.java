package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 커넥션 풀 고갈(Connection Pool Starvation) 시나리오 및 가용성(Availability) 검증 테스트
 * * [시뮬레이션 환경 제약]
 * 1. hikari.maximum-pool-size=5 : 커넥션 풀을 극단적으로 제한
 * 2. hikari.connection-timeout=250 : 0.25초 내 커넥션 획득 실패 시 에러 발생
 * 3. test.simulation.delay-ms=150 : AOP를 통해 트랜잭션 내부 로직에 의도적 지연(150ms) 주입
 * * 트래픽 폭주 상황에서 'DB 비관적 락(점유 시간 증가)'과 'Redis 분산 락'의
 * 시스템 전체 가용성(단순 조회 요청의 성공 여부) 차이를 증명합니다.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.connection-timeout=250",
        "test.simulation.delay-ms=150",
        "app.order.lock.wait-time=30"
})
@ActiveProfiles("test")
public class OrderAvailabilityIntegrationTest extends MariaDbRedisIntegrationTest {

    @Autowired
    private RedissonLockStockFacade redissonLockStockFacade;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("서버 생존 테스트: 주문 폭주 시 DB락과 Redis락 비교")
    void compareAvailability() throws InterruptedException {
        // [1] DB 비관적 락 시나리오 검증
        // 트랜잭션 내부에서 대기 시간(지연)이 발생하여 커넥션 풀이 빠르게 고갈됩니다.
        // 이로 인해 락과 무관한 '단순 조회' 요청마저 커넥션 타임아웃으로 실패해야 합니다.
        System.out.println("\n========== [1. DB 비관적 락 테스트 시작] ==========");
        TestResult dbResult = runTest("DB 비관적 락", (id, pid) -> decreaseStockWhileHoldingConnection(pid));

        assertThat(dbResult.remainingStock()).isGreaterThan(50);
        assertThat(dbResult.viewFailCount()).isGreaterThan(0); // 커넥션 고갈에 따른 조회 장애 발생 검증

        tearDown();

        // [2] Redis 분산 락 시나리오 검증
        // DB 트랜잭션 진입 전 Redis에서 대기열을 제어하므로, DB 커넥션 점유 시간이 짧게 유지됩니다.
        // 따라서 '단순 조회' 요청이 커넥션 풀의 여유분을 확보하여 타임아웃 없이 정상 처리되어야 합니다.
        System.out.println("\n========== [2. Redis 분산 락 테스트 시작] ==========");
        TestResult redisResult = runTest("Redis 분산 락", (id, pid) -> redissonLockStockFacade.order(id, pid, 1));

        assertThat(redisResult.remainingStock()).isBetween(50,55);
        assertThat(redisResult.viewFailCount()).isEqualTo(0);
    }

    record TestResult(int remainingStock, int viewFailCount) {
    }

    private void decreaseStockWhileHoldingConnection(Long productId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Product product = productRepository.findByIdWithPessimisticLock(productId).orElseThrow();
            try {
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("DB connection 점유 시뮬레이션이 중단됐습니다.", exception);
            }
            product.removeStock(1);
        });
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

        // 1. 재고 차감(동시성) 트래픽 발생
        for (int i = 0; i < orderCount; i++) {
            executor.submit(() -> {
                try {
                    strategy.decrease(user.getId(), product.getId());
                } catch (Exception e) {
                    // 의도적인 락 획득 실패 및 타임아웃 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        // 2. 동기화와 무관한 단순 읽기(조회) 트래픽 병렬 발생
        for (int i = 0; i < viewCount; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(100); // 병목 상황 진입을 위한 의도적 대기
                    productRepository.findById(product.getId());
                    viewSuccess.getAndIncrement();
                } catch (Exception e) {
                    viewFail.getAndIncrement();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(2, java.util.concurrent.TimeUnit.MINUTES)).isTrue();
        executor.shutdownNow();
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
