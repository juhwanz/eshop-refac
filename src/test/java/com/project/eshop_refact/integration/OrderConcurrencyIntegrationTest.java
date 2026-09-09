package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제한된 동시 주문 시나리오에서 성공·실패 수와 최종 재고를 검증합니다.
 */
@SpringBootTest(properties = {
        // 테스트 스레드가 락 획득 결과를 기다릴 수 있도록 한 테스트 전용 설정입니다.
        "spring.datasource.hikari.maximum-pool-size=50",
        "spring.datasource.hikari.connection-timeout=5000",
        "spring.jpa.properties.hibernate.show_sql=false",
        "app.order.lock.wait-time=120",
        "logging.level.root=error"
})
public class OrderConcurrencyIntegrationTest extends MariaDbRedisIntegrationTest {

    @Autowired private RedissonLockStockFacade redissonLockStockFacade;
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
    @DisplayName("[정합성 검증] 40개 재고 45명 동시 주문 -> 40개 성공, 5개 실패.")
    void verifyConcurrencyLimit() throws InterruptedException {
        // Given
        int stockQuantity = 40;
        int threadCount = 45;

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

}
