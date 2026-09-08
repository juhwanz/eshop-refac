package com.project.eshop_refact.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.domain.order.OrderIdempotencyService;
import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.order.OrderRequestIdentity;
import com.project.eshop_refact.domain.order.OrderService;
import com.project.eshop_refact.domain.order.strategy.GeneralStockStrategy;
import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.order.lock.wait-time=30")
@AutoConfigureMockMvc
class OrderDurableIdempotencyIntegrationTest extends MariaDbRedisIntegrationTest {
    @Autowired OrderIdempotencyService service;
    @Autowired OrderService orders;
    @SpyBean RedissonLockStockFacade facade;
    @Autowired OrderRepository repository;
    @SpyBean GeneralStockStrategy stockStrategy;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper mapper;
    @Autowired MeterRegistry metrics;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    User owner;
    Product product;

    @BeforeEach
    void prepare() {
        owner = users.save(new User("durable@test.com", "pw", "owner", UserRoleEnum.USER));
        product = products.save(new Product("durable", 1000, 1));
        allow(owner.getId(), product.getId());
    }

    @AfterEach
    void clean() {
        repository.deleteAll();
        products.deleteAll();
        users.deleteAll();
    }

    @Test
    void cacheMissAndCancellationReturnOriginalOrderWithoutQueuePermission() {
        Long id = request("key", product.getId(), 1);
        redis.delete(cacheKey("key"));
        assertThat(request("key", product.getId(), 1)).isEqualTo(id);
        facade.cancelOrder(id, owner.getId());
        redis.delete(cacheKey("key"));
        assertThat(request("key", product.getId(), 1)).isEqualTo(id);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stock(product.getId())).isEqualTo(1);
    }

    @Test
    void differentPayloadConflictsWithWarmAndColdCache() {
        request("key", product.getId(), 1);
        assertConflict(() -> request("key", product.getId(), 2));
        redis.delete(cacheKey("key"));
        Product other = products.save(new Product("other", 1000, 10));
        assertConflict(() -> request("key", other.getId(), 1));
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stock(other.getId())).isEqualTo(10);
    }

    @Test
    void simultaneousRequestsReturnOneOrderEvenWithLastStock() throws Exception {
        List<Callable<Long>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(() -> request("concurrent", product.getId(), 1));
        }
        List<Long> ids = concurrent(calls);
        assertThat(ids).hasSize(10).containsOnly(ids.getFirst());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stock(product.getId())).isZero();
    }

    @Test
    void competingInsertsOnDifferentProductsRollbackLoser() throws Exception {
        Product other = products.save(new Product("other", 1000, 1));
        allow(owner.getId(), other.getId());
        CyclicBarrier inserts = new CyclicBarrier(2);
        // 두 트랜잭션 모두 기존 결과 조회를 끝낸 뒤 INSERT하도록 실제 경합을 보장합니다.
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            inserts.await(10, TimeUnit.SECONDS);
            return result;
        }).when(stockStrategy).decrease(anyLong(), anyInt());
        List<Object> results = concurrent(List.of(
                () -> outcome(product.getId()), () -> outcome(other.getId())));
        assertThat(results.stream().filter(Long.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(value -> value == ErrorCode.IDEMPOTENCY_CONFLICT).count()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stock(product.getId()) + stock(other.getId())).isEqualTo(1);
    }

    @Test
    void databaseRecoversSamePayloadWhenRedisLockIsBypassed() throws Exception {
        CyclicBarrier inserts = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            inserts.await(10, TimeUnit.SECONDS);
            return result;
        }).when(stockStrategy).decrease(anyLong(), anyInt());
        // DB 방어선을 독립 검증하기 위해 이 테스트에서만 상품 락을 우회합니다.
        doAnswer(invocation -> orders.order(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2), invocation.getArgument(3)))
                .when(facade).order(anyLong(), anyLong(), anyInt(), any(OrderRequestIdentity.class));
        List<Long> results = concurrent(List.of(
                () -> request("db-race", product.getId(), 1),
                () -> request("db-race", product.getId(), 1)));
        assertThat(results).hasSize(2).containsOnly(results.getFirst());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stock(product.getId())).isZero();
    }

    @Test
    void expiredOwnersCleanupPreservesReplacementToken() {
        doAnswer(invocation -> {
            redis.opsForValue().set(cacheKey("expired"), "PROCESSING:replacement");
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }).when(stockStrategy).decrease(anyLong(), anyInt());
        assertThatThrownBy(() -> request("expired", product.getId(), 1))
                .isInstanceOf(BusinessException.class);
        assertThat(redis.opsForValue().get(cacheKey("expired"))).isEqualTo("PROCESSING:replacement");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisWriteFailureAfterCommitStillReturnsAndRecoversOrder() {
        RedisTemplate<String, String> broken = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(broken.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        doThrow(new IllegalStateException("injected Redis write failure"))
                .when(values).set(anyString(), anyString(), eq(24L), eq(TimeUnit.HOURS));
        OrderIdempotencyService withBrokenCache = new OrderIdempotencyService(broken, mapper, facade, metrics, orders);
        Long first = withBrokenCache.processOrderWithIdempotency("write-failure", owner.getId(), product.getId(), 1).getOrderId();
        Long retry = service.processOrderWithIdempotency("write-failure", owner.getId(), product.getId(), 1).getOrderId();
        assertThat(retry).isEqualTo(first);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stock(product.getId())).isZero();
    }

    @Test
    void httpRetryWorksAfterQueuePermissionRemovedButNewRequestIsRejected() throws Exception {
        String body = "{\"productId\":" + product.getId() + ",\"count\":1}";
        Long id = request("http", product.getId(), 1);
        redis.delete(cacheKey("http"));
        mvc.perform(post("/api/orders").with(user(new UserDetailsImpl(owner))).with(csrf())
                        .header("Idempotency-Key", "http").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.orderId").value(id));
        mvc.perform(post("/api/orders").with(user(new UserDetailsImpl(owner))).with(csrf())
                        .header("Idempotency-Key", "new").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void activePermissionForOneProductCannotOrderAnotherProduct() {
        Product other = products.save(new Product("other", 1000, 1));

        assertThatThrownBy(() -> request("wrong-product", other.getId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUEUE_WAITING);

        assertThat(repository.count()).isZero();
        assertThat(stock(other.getId())).isEqualTo(1);
        assertThat(redis.opsForZSet().score(
                "queue:{" + product.getId() + "}:active",
                owner.getId().toString()
        )).isNotNull();
    }

    @Test
    void failedOrderReleasesOwnedMarkerAndAllowsRetry() {
        assertThatThrownBy(() -> request("failure", product.getId(), 2))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
        assertThat(redis.hasKey(cacheKey("failure"))).isFalse();
        assertThat(repository.count()).isZero();
        assertThat(stock(product.getId())).isEqualTo(1);
        allow(owner.getId(), product.getId());
        request("failure", product.getId(), 1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void failureDoesNotDeleteAnotherRequestsMarker() {
        redis.opsForValue().set(cacheKey("occupied"), "PROCESSING:another-owner");
        assertThatThrownBy(() -> request("occupied", product.getId(), 2))
                .isInstanceOf(BusinessException.class);
        assertThat(redis.opsForValue().get(cacheKey("occupied"))).isEqualTo("PROCESSING:another-owner");
    }

    @Test
    void generatedSchemaEnforcesExactUserScopedKeys() {
        request("Key", product.getId(), 1);
        Product other = products.save(new Product("other", 1000, 10));
        allow(owner.getId(), other.getId());
        request("key", other.getId(), 1);
        User second = users.save(new User("second@test.com", "pw", "second", UserRoleEnum.USER));
        allow(second.getId(), other.getId());
        service.processOrderWithIdempotency("Key", second.getId(), other.getId(), 1);
        assertThat(repository.count()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_schema=database() and table_name='orders' and column_name='idempotency_key'", String.class)).isEqualTo("varbinary");
        assertThat(jdbc.queryForObject("select character_maximum_length from information_schema.columns where table_schema=database() and table_name='orders' and column_name='idempotency_key'", Integer.class)).isEqualTo(128);
        assertThat(jdbc.queryForObject("select character_maximum_length from information_schema.columns where table_schema=database() and table_name='orders' and column_name='request_fingerprint'", Integer.class)).isEqualTo(64);
        assertThat(jdbc.queryForList("select column_name from information_schema.statistics where table_schema=database() and table_name='orders' and index_name='uk_orders_user_idempotency' and non_unique=0 order by seq_in_index", String.class))
                .containsExactly("user_id", "idempotency_key");
        assertThatThrownBy(() -> jdbc.update("insert into orders(user_id, order_date, status, idempotency_key, request_fingerprint) values (?, current_timestamp, 'ORDER', ?, ?)",
                owner.getId(), OrderRequestIdentity.of("Key", product.getId(), 1).keyBytes(), "a".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Object outcome(Long productId) {
        try {
            return request("race", productId, 1);
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private <T> List<T> concurrent(List<Callable<T>> calls) throws Exception {
        var executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timeout");
                    }
                    return call.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> result = new ArrayList<>();
            for (Future<T> future : futures) {
                result.add(future.get(40, TimeUnit.SECONDS));
            }
            return result;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertConflict(Callable<Long> call) {
        assertThatThrownBy(call::call).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    private Long request(String key, Long productId, int count) {
        return service.processOrderWithIdempotency(key, owner.getId(), productId, count).getOrderId();
    }

    private void allow(Long userId, Long productId) {
        redis.opsForZSet().add(
                "queue:{" + productId + "}:active",
                userId.toString(),
                System.currentTimeMillis() + 600_000
        );
    }

    private String cacheKey(String key) {
        return "idempotency:order:" + owner.getId() + ":" + key;
    }

    private int stock(Long productId) {
        return products.findById(productId).orElseThrow().getStockQuantity();
    }
}
