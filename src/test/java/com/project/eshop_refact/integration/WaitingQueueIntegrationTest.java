package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.queue.QueueStatus;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.queue.promotion-size-per-product=2",
        "app.queue.product-scan-limit=2"
})
class WaitingQueueIntegrationTest extends MariaDbRedisIntegrationTest {

    @Autowired WaitingQueueService queue;
    @Autowired ProductRepository products;
    @Autowired StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        products.deleteAll();
    }

    @Test
    void productQueuesAndActivePermissionsAreIndependent() {
        Product first = products.save(new Product("first", 100, 10));
        Product second = products.save(new Product("second", 100, 10));

        assertThat(queue.register(first.getId(), 1L).response().getRank()).isEqualTo(1L);
        assertThat(queue.register(second.getId(), 1L).response().getRank()).isEqualTo(1L);

        assertThat(queue.allowWaitingProducts()).isEqualTo(2);
        assertThat(queue.isAllowed(1L, first.getId())).isTrue();
        assertThat(queue.isAllowed(1L, second.getId())).isTrue();

        queue.removeUser(1L, first.getId());
        assertThat(queue.isAllowed(1L, first.getId())).isFalse();
        assertThat(queue.isAllowed(1L, second.getId())).isTrue();
    }

    @Test
    void duplicateRegistrationKeepsTheOriginalSequenceAndRank() {
        Product product = products.save(new Product("duplicate", 100, 10));

        WaitingQueueService.Registration first = queue.register(product.getId(), 1L);
        Double firstScore = redis.opsForZSet().score(waitingKey(product.getId()), "1");
        WaitingQueueService.Registration duplicate = queue.register(product.getId(), 1L);

        assertThat(first.created()).isTrue();
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.response().getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(duplicate.response().getRank()).isEqualTo(1L);
        assertThat(redis.opsForZSet().score(waitingKey(product.getId()), "1")).isEqualTo(firstScore);
        assertThat(redis.opsForValue().get(sequenceKey(product.getId()))).isEqualTo("1");
    }

    @Test
    void concurrentRegistrationUsesUniqueMonotonicSequences() throws Exception {
        Product product = products.save(new Product("concurrent", 100, 30));
        int count = 20;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(count);
        try {
            List<java.util.concurrent.Future<WaitingQueueService.Registration>> futures = new ArrayList<>();
            for (long userId = 1; userId <= count; userId++) {
                long registeredUserId = userId;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timeout");
                    }
                    return queue.register(product.getId(), registeredUserId);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> ranks = new ArrayList<>();
            for (var future : futures) {
                ranks.add(future.get(20, TimeUnit.SECONDS).response().getRank());
            }

            assertThat(ranks).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, count).boxed().toList()
            );
            assertThat(redis.opsForZSet().rangeWithScores(waitingKey(product.getId()), 0, -1))
                    .extracting(tuple -> tuple.getScore().longValue())
                    .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, count).boxed().toList());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void promotionAtomicallyMovesOnlyTheConfiguredHeadUsers() {
        Product product = products.save(new Product("promotion", 100, 10));
        queue.register(product.getId(), 1L);
        queue.register(product.getId(), 2L);
        queue.register(product.getId(), 3L);

        assertThat(queue.allowWaitingProducts()).isEqualTo(2);

        Set<String> waiting = redis.opsForZSet().range(waitingKey(product.getId()), 0, -1);
        Set<String> active = redis.opsForZSet().range(activeKey(product.getId()), 0, -1);
        assertThat(waiting).containsExactly("3");
        assertThat(active).containsExactlyInAnyOrder("1", "2");
        assertThat(waiting).doesNotContainAnyElementsOf(active);
        assertThat(queue.getStatus(product.getId(), 3L).getRank()).isEqualTo(1L);
    }

    @Test
    void expiredPermissionIsRejectedAndRemoved() {
        Product product = products.save(new Product("expired", 100, 10));
        redis.opsForZSet().add(activeKey(product.getId()), "1", 0);

        assertThat(queue.isAllowed(1L, product.getId())).isFalse();
        assertThat(redis.opsForZSet().score(activeKey(product.getId()), "1")).isNull();
    }

    @Test
    void onlyABoundedNumberOfProductsIsPromotedPerRun() {
        Product first = products.save(new Product("first", 100, 10));
        Product second = products.save(new Product("second", 100, 10));
        Product third = products.save(new Product("third", 100, 10));
        queue.register(first.getId(), 1L);
        queue.register(second.getId(), 2L);
        queue.register(third.getId(), 3L);

        assertThat(queue.allowWaitingProducts()).isEqualTo(2);
        assertThat(List.of(
                queue.isAllowed(1L, first.getId()),
                queue.isAllowed(2L, second.getId()),
                queue.isAllowed(3L, third.getId())
        )).containsExactlyInAnyOrder(true, true, false);
    }

    @Test
    void missingProductCannotBeRegistered() {
        assertThatThrownBy(() -> queue.register(Long.MAX_VALUE, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
        assertThat(redis.hasKey("queue:waiting-products")).isFalse();
    }

    private String waitingKey(Long productId) {
        return "queue:{" + productId + "}:waiting";
    }

    private String sequenceKey(Long productId) {
        return "queue:{" + productId + "}:sequence";
    }

    private String activeKey(Long productId) {
        return "queue:{" + productId + "}:active";
    }
}
