package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.*;
import com.project.eshop_refact.dto.OrderDto;
import com.project.eshop_refact.repository.OrderRepository;
import com.project.eshop_refact.repository.ProductRepository;
import com.project.eshop_refact.repository.UserRepository;
import com.project.eshop_refact.service.OrderService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
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

@SpringBootTest
@Transactional
public class OrderQueryIntegrationTest {

    @Autowired OrderRepository orderRepository;
    @Autowired OrderService orderService;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired EntityManager em;

    private Long testUserId; // 조회용 ID를 저장할 클래스 변수

    @BeforeEach
    void setup(){
        User user = userRepository.save(new User("nplus1@test.com", "1234", "tester", UserRoleEnum.USER));
        testUserId = user.getId(); // 저장된 유저의 ID 기록

        Product product = productRepository.save(new Product("Test Item", 1000, 100));

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            OrderItem item = OrderItem.createOrderItem(product, 1);
            orders.add(Order.createOrder(user, List.of(item)));
        }
        orderRepository.saveAll(orders);

        em.flush();
        em.clear(); // 1차 캐시 비우기 (쿼리 발생 유도)
    }

    @Test
    @DisplayName("N+1 검증 및 데이터 정합성 확인")
    void checkNPlusOne() {
        System.out.println("\n========== [조회 시작] ==========");

        // When: 저장해둔 ID를 바로 사용해서 조회
        Page<OrderDto.Response> result =
                orderService.getOrders(testUserId, PageRequest.of(0, 10));

        System.out.println("========== [조회 종료] ==========\n");

        // Then: 10개의 주문이 모두 정상적으로 조회되었는지 검증
        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getContent().get(0).getOrderId()).isNotNull();
    }
}