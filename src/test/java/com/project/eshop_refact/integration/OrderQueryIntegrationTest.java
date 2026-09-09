package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.order.Order;
import com.project.eshop_refact.domain.order.OrderDto;
import com.project.eshop_refact.domain.order.OrderItem;
import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.order.OrderService;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Transactional;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 조회 시 orderItems와 product 지연 로딩이 batch fetch로 묶이는지 검증합니다.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_batch_fetch_size=100",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
public class OrderQueryIntegrationTest extends MariaDbRedisIntegrationTest {

    @Autowired OrderRepository orderRepository;
    @Autowired OrderService orderService;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory entityManagerFactory;

    private Long testUserId;

    @BeforeEach
    void setup() {
        User user = userRepository.save(new User("nplus1@test.com", "1234", "tester", UserRoleEnum.USER));
        testUserId = user.getId();

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Product product = productRepository.save(new Product("Test Item " + i, 1000, 100));
            OrderItem item = OrderItem.createOrderItem(product, 1);
            orders.add(Order.createOrder(user, List.of(item)));
        }
        orderRepository.saveAll(orders);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("주문 10건의 항목과 상품을 batch fetch하여 SQL 수가 주문 수에 비례하지 않는다")
    void fetchesOrderItemsAndProductsInBatches() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Page<OrderDto.Response> result = orderService.getOrders(testUserId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getContent()).allSatisfy(response ->
                assertThat(response.getOrderItems()).hasSize(1));
        // user, orders, count, order_item batch, product batch 쿼리만 허용합니다.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }
}
