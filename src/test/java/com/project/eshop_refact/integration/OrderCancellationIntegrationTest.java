package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.order.Order;
import com.project.eshop_refact.domain.order.OrderItem;
import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.order.OrderService;
import com.project.eshop_refact.domain.order.OrderStatus;
import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductDto;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.product.ProductService;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.order.lock.wait-time=30")
class OrderCancellationIntegrationTest extends MariaDbRedisIntegrationTest {

    private static final int INITIAL_STOCK = 10;
    private static final int ORDER_COUNT = 2;

    @Autowired
    private OrderService orderService;
    @Autowired
    private RedissonLockStockFacade redissonLockStockFacade;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        productCache().clear();
    }

    @Test
    @DisplayName("순차 중복 취소는 성공하지만 재고와 캐시는 한 번만 변경한다")
    void sequentialCancellationIsIdempotent() {
        TestOrder testOrder = createOrder();

        redissonLockStockFacade.cancelOrder(testOrder.orderId(), testOrder.ownerId());
        ProductDto.Response cachedProduct = productService.getProductById(testOrder.productId());
        assertThat(cachedProduct.getStockQuantity()).isEqualTo(INITIAL_STOCK);
        assertThat(productCache().get(testOrder.productId())).isNotNull();

        redissonLockStockFacade.cancelOrder(testOrder.orderId(), testOrder.ownerId());

        assertThat(orderRepository.findById(testOrder.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCEL);
        assertThat(productRepository.findById(testOrder.productId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK);
        assertThat(productCache().get(testOrder.productId())).isNotNull();
    }

    @Test
    @DisplayName("같은 주문의 동시 취소는 주문 행에서 직렬화되어 재고를 한 번만 복구한다")
    void concurrentCancellationRestoresStockOnce() throws InterruptedException {
        TestOrder testOrder = createOrder();
        productService.getProductById(testOrder.productId());

        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(requestCount);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        orderService.cancelOrder(testOrder.orderId(), testOrder.ownerId());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        failures.add(exception);
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        completed.countDown();
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(completed.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(failures).isEmpty();
        assertThat(orderRepository.findById(testOrder.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCEL);
        assertThat(productRepository.findById(testOrder.productId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK);
        assertThat(productCache().get(testOrder.productId())).isNull();
    }

    @Test
    @DisplayName("취소 캐시는 트랜잭션 커밋 전에는 유지되고 커밋 후 제거된다")
    void cacheIsEvictedOnlyAfterCommit() {
        TestOrder testOrder = createOrder();
        productService.getProductById(testOrder.productId());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            orderService.cancelOrder(testOrder.orderId(), testOrder.ownerId());
            assertThat(productCache().get(testOrder.productId())).isNotNull();
        });

        assertThat(productCache().get(testOrder.productId())).isNull();
        assertThat(productService.getProductById(testOrder.productId()).getStockQuantity())
                .isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("취소 트랜잭션이 롤백되면 주문 상태와 재고 및 기존 캐시가 유지된다")
    void rollbackKeepsOrderStockAndCache() {
        TestOrder testOrder = createOrder();
        ProductDto.Response cachedProduct = productService.getProductById(testOrder.productId());
        assertThat(cachedProduct.getStockQuantity()).isEqualTo(INITIAL_STOCK - ORDER_COUNT);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            orderService.cancelOrder(testOrder.orderId(), testOrder.ownerId());
            assertThat(productCache().get(testOrder.productId())).isNotNull();
            status.setRollbackOnly();
        });

        assertThat(orderRepository.findById(testOrder.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ORDER);
        assertThat(productRepository.findById(testOrder.productId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_COUNT);
        assertThat(productCache().get(testOrder.productId())).isNotNull();
        assertThat(productService.getProductById(testOrder.productId()).getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_COUNT);
    }

    @Test
    @DisplayName("다른 사용자는 이미 생성된 주문을 취소할 수 없다")
    void otherUserCannotCancelOrder() {
        TestOrder testOrder = createOrder();
        User otherUser = userRepository.save(
                new User("other@test.com", "1234", "other", UserRoleEnum.USER)
        );
        productService.getProductById(testOrder.productId());

        assertThatThrownBy(() -> redissonLockStockFacade.cancelOrder(testOrder.orderId(), otherUser.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_ACCESS);

        assertThat(orderRepository.findById(testOrder.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ORDER);
        assertThat(productRepository.findById(testOrder.productId()).orElseThrow().getStockQuantity())
                .isEqualTo(INITIAL_STOCK - ORDER_COUNT);
        assertThat(productCache().get(testOrder.productId())).isNotNull();
    }

    private TestOrder createOrder() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            User owner = userRepository.save(
                    new User("owner@test.com", "1234", "owner", UserRoleEnum.USER)
            );
            Product product = productRepository.save(
                    new Product("취소 테스트 상품", 10000, INITIAL_STOCK)
            );
            product.removeStock(ORDER_COUNT);

            OrderItem orderItem = OrderItem.createOrderItem(product, ORDER_COUNT);
            Order order = orderRepository.save(Order.createOrder(owner, List.of(orderItem)));

            return new TestOrder(order.getId(), owner.getId(), product.getId());
        });
    }

    private Cache productCache() {
        Cache cache = cacheManager.getCache("products");
        if (cache == null) {
            throw new IllegalStateException("products 캐시가 구성되지 않았습니다.");
        }
        return cache;
    }

    private record TestOrder(Long orderId, Long ownerId, Long productId) {
    }
}
