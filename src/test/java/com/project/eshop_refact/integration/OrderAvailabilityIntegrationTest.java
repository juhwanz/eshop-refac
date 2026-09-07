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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 커넥션 풀 고갈(Connection Pool Starvation) 시나리오 및 가용성(Availability) 검증 테스트
 * * [시뮬레이션 환경 제약]
 * 1. hikari.maximum-pool-size=5 : 커넥션 풀을 극단적으로 제한
 * 2. hikari.connection-timeout=250 : 0.25초 내 커넥션 획득 실패 시 에러 발생
 * 3. DB 경로는 비관적 락 트랜잭션 5개가 풀을 점유한 뒤 조회를 시작하도록 동기화
 * 4. test.simulation.delay-ms=150 : Redis 락 경로의 트랜잭션 내부 로직에 지연 주입
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

    private static final int CONNECTION_POOL_SIZE = 5;
    private static final int ORDER_COUNT = 50;
    private static final int VIEW_COUNT = 20;

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
        // 비관적 락을 획득한 트랜잭션이 커넥션 풀 전체를 점유하도록 동기화합니다.
        // 이로 인해 락과 무관한 '단순 조회' 요청마저 커넥션 타임아웃으로 실패해야 합니다.
        System.out.println("\n========== [1. DB 비관적 락 테스트 시작] ==========");
        int dbViewFailCount = runDbLockTest();

        assertThat(dbViewFailCount).isEqualTo(VIEW_COUNT);

        tearDown();

        // [2] Redis 분산 락 시나리오 검증
        // DB 트랜잭션 진입 전 Redis에서 대기열을 제어하므로, DB 커넥션 점유 시간이 짧게 유지됩니다.
        // 따라서 '단순 조회' 요청이 커넥션 풀의 여유분을 확보하여 타임아웃 없이 정상 처리되어야 합니다.
        System.out.println("\n========== [2. Redis 분산 락 테스트 시작] ==========");
        TestResult redisResult = runRedisLockTest();

        assertThat(redisResult.remainingStock()).isBetween(50,55);
        assertThat(redisResult.viewFailCount()).isEqualTo(0);
    }

    record TestResult(int remainingStock, int viewFailCount) {
    }

    private void holdPessimisticLock(
            Long productId,
            CountDownLatch locksAcquired,
            CountDownLatch releaseLocks
    ) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Product product = productRepository.findByIdWithPessimisticLock(productId).orElseThrow();
            locksAcquired.countDown();
            await(releaseLocks, "DB connection 점유 시뮬레이션이 중단됐습니다.");
            product.removeStock(1);
        });
    }

    private int runDbLockTest() throws InterruptedException {
        Product viewTarget = productRepository.save(new Product("View Target", 10000, 100));
        List<Long> lockTargetIds = new ArrayList<>();
        for (int i = 0; i < CONNECTION_POOL_SIZE; i++) {
            Product lockTarget = productRepository.save(new Product("Lock Target " + i, 10000, 100));
            lockTargetIds.add(lockTarget.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONNECTION_POOL_SIZE + VIEW_COUNT);
        CountDownLatch locksAcquired = new CountDownLatch(CONNECTION_POOL_SIZE);
        CountDownLatch releaseLocks = new CountDownLatch(1);
        CountDownLatch lockTasksDone = new CountDownLatch(CONNECTION_POOL_SIZE);
        CountDownLatch viewTasksDone = new CountDownLatch(VIEW_COUNT);
        AtomicInteger viewFail = new AtomicInteger();

        long start = System.currentTimeMillis();

        try {
            for (Long lockTargetId : lockTargetIds) {
                executor.submit(() -> {
                    try {
                        holdPessimisticLock(lockTargetId, locksAcquired, releaseLocks);
                    } finally {
                        lockTasksDone.countDown();
                    }
                });
            }

            assertThat(locksAcquired.await(10, TimeUnit.SECONDS)).isTrue();
            submitViewTasks(executor, viewTarget.getId(), viewTasksDone, viewFail, null);
            assertThat(viewTasksDone.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseLocks.countDown();
            executor.shutdown();
        }

        assertThat(lockTasksDone.await(2, TimeUnit.MINUTES)).isTrue();

        printResult("DB 비관적 락", start, VIEW_COUNT - viewFail.get(), viewFail.get());
        return viewFail.get();
    }

    private TestResult runRedisLockTest() throws InterruptedException {

        Product product = productRepository.save(new Product("Hot Deal", 10000, 100));
        User user = userRepository.save(new User("tester", "1234", "name", UserRoleEnum.USER));

        ExecutorService executor = Executors.newFixedThreadPool(ORDER_COUNT + VIEW_COUNT);
        CountDownLatch ready = new CountDownLatch(ORDER_COUNT + VIEW_COUNT);
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(ORDER_COUNT + VIEW_COUNT);

        AtomicInteger viewSuccess = new AtomicInteger();
        AtomicInteger viewFail = new AtomicInteger();

        long start = System.currentTimeMillis();

        try {
            // 1. 재고 차감(동시성) 트래픽 발생
            for (int i = 0; i < ORDER_COUNT; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        await(startTogether, "Redis 락 주문 시뮬레이션이 중단됐습니다.");
                        redissonLockStockFacade.order(user.getId(), product.getId(), 1);
                    } catch (Exception e) {
                        // 의도적인 락 획득 실패 및 타임아웃 예외 무시
                    } finally {
                        completed.countDown();
                    }
                });
            }

            // 2. 동기화와 무관한 단순 읽기(조회) 트래픽 병렬 발생
            submitViewTasks(executor, product.getId(), completed, viewFail, new ViewStart(ready, startTogether, viewSuccess));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            assertThat(completed.await(2, TimeUnit.MINUTES)).isTrue();
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
        }

        Product finalProduct = productRepository.findById(product.getId()).orElseThrow();
        printResult("Redis 분산 락", start, viewSuccess.get(), viewFail.get());

        return new TestResult(finalProduct.getStockQuantity(), viewFail.get());
    }

    private void submitViewTasks(
            ExecutorService executor,
            Long productId,
            CountDownLatch completed,
            AtomicInteger viewFail,
            ViewStart viewStart
    ) {
        for (int i = 0; i < VIEW_COUNT; i++) {
            executor.submit(() -> {
                if (viewStart != null) {
                    viewStart.ready().countDown();
                    await(viewStart.startTogether(), "조회 시뮬레이션이 중단됐습니다.");
                }
                try {
                    productRepository.findById(productId);
                    if (viewStart != null) {
                        viewStart.viewSuccess().incrementAndGet();
                    }
                } catch (Exception e) {
                    viewFail.incrementAndGet();
                } finally {
                    completed.countDown();
                }
            });
        }
    }

    private void printResult(String method, long start, int viewSuccess, int viewFail) {
        System.out.println("  [" + method + " 결과]");
        System.out.println("   - 총 소요 시간: " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("   - 조회 성공: " + viewSuccess);
        System.out.println("   - 조회 실패: " + viewFail);
    }

    private void await(CountDownLatch latch, String message) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        }
    }

    record ViewStart(
            CountDownLatch ready,
            CountDownLatch startTogether,
            AtomicInteger viewSuccess
    ) {
    }
}
