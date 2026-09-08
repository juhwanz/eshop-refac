package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.order.OrderService;
import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.product.ProductService;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "test.simulation.delay-ms=0")
class StockProtectionIntegrationTest extends MariaDbRedisIntegrationTest {
    @Autowired RedissonClient client;
    @Autowired OrderService orders;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired WaitingQueueService queue;
    @Autowired JdbcTemplate jdbc;
    @Autowired HikariDataSource dataSource;
    @SpyBean ProductService productService;

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        products.deleteAll();
        users.deleteAll();
    }

    @Test
    void watchdogProtectsLongTransactionWithoutConnectionsForWaiters() throws Exception {
        Long productId = products.save(new Product("watchdog", 100, 2)).getId();
        Long userId = users.save(new User("watchdog@test.com", "password", "watchdog", UserRoleEnum.USER)).getId();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger entries = new AtomicInteger();
        doAnswer(invocation -> {
            Object product = invocation.callRealMethod();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (entries.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
            }
            return product;
        }).when(productService).decreaseStockWithoutLock(anyLong(), anyInt());

        Config config = new Config(client.getConfig());
        config.setLockWatchdogTimeout(3000);
        RedissonClient shortWatchdog = Redisson.create(config);
        var facade = new RedissonLockStockFacade(shortWatchdog, orders, queue);
        ReflectionTestUtils.setField(facade, "waitTime", 10L);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> facade.order(userId, productId, 1));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch contenderStarted = new CountDownLatch(1);
            var second = executor.submit(() -> {
                contenderStarted.countDown();
                return facade.order(userId, productId, 1);
            });
            assertThat(contenderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(4, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(entries.get()).isEqualTo(1);
            assertThat(shortWatchdog.getLock("product:stock:" + productId).remainTimeToLive()).isPositive();
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(entries.get()).isEqualTo(2);
            assertThat(orderRepository.count()).isEqualTo(2);
            assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            shortWatchdog.shutdown();
        }
    }

    @Test
    void schemaUpdateRequiresExplicitCheckForExistingTable() {
        jdbc.execute("ALTER TABLE products DROP CONSTRAINT chk_products_stock_nonnegative");
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(Product.class)
                .buildMetadata().buildSessionFactory()) {
            assertThat(factory.isOpen()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND CONSTRAINT_NAME = 'chk_products_stock_nonnegative'", Integer.class)).isZero();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
            jdbc.execute("ALTER TABLE products ADD CONSTRAINT chk_products_stock_nonnegative CHECK (stock_quantity >= 0)");
        }
    }

    @Test
    void databaseRejectsNegativeStock() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND CONSTRAINT_NAME = 'chk_products_stock_nonnegative' AND CONSTRAINT_TYPE = 'CHECK'", Integer.class)).isEqualTo(1);
        Long id = products.save(new Product("zero", 100, 0)).getId();
        jdbc.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", id);
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET stock_quantity = -1 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO products (name, price, stock_quantity) VALUES ('invalid', 100, -1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(products.findById(id).orElseThrow().getStockQuantity()).isEqualTo(1);
    }
}
