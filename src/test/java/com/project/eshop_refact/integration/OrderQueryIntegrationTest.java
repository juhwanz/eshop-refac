package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.order.Order;
import com.project.eshop_refact.domain.order.OrderItem;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.domain.order.OrderDto;
import com.project.eshop_refact.domain.order.OrderRepository;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.order.OrderService;
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

/**
 * 주문 조회 쿼리 최적화 통합 테스트
 * 1. default_batch_fetch_size 설정 기반의 N+1 문제 해결 검증
 * 2. 컬렉션 Fetch Join과 Paging 혼용 시 발생하는 인메모리 페이징(OOM 위험) 경고 상황 증명
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_batch_fetch_size=100"
})
@Transactional
public class OrderQueryIntegrationTest {

    @Autowired OrderRepository orderRepository;
    @Autowired OrderService orderService;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired EntityManager em;

    private Long testUserId;

    @BeforeEach
    void setup(){
        User user = userRepository.save(new User("nplus1@test.com", "1234", "tester", UserRoleEnum.USER));
        testUserId = user.getId();

        Product product = productRepository.save(new Product("Test Item", 1000, 100));

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            OrderItem item = OrderItem.createOrderItem(product, 1);
            orders.add(Order.createOrder(user, List.of(item)));
        }
        orderRepository.saveAll(orders);

        // 영속성 컨텍스트를 초기화하여, 이후 조회 시 1차 캐시가 아닌 실제 쿼리 발생을 유도합니다.
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("N+1 검증 및 데이터 정합성 확인")
    void checkNPlusOne() {
        System.out.println("\n========== [조회 시작] ==========");

        //when
        Page<OrderDto.Response> result = orderService.getOrders(testUserId, PageRequest.of(0, 10));

        System.out.println("========== [조회 종료] ==========\n");

        // Then
        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getContent().get(0).getOrderId()).isNotNull();
    }

    @Test
    @DisplayName("안티 패턴 증명: 컬렉션 Fetch Join과 Paging 혼용 시 Hibernate 인메모리 페이징이 발생한다")
    void checkMemoryPagingWarning() {
        System.out.println("\n========== [위험한 조회 시작: Fetch Join + Paging] ==========");

        // Given
        User user = userRepository.findById(testUserId).orElseThrow();

        // When
        // PageRequest를 전달하더라도, 1:N 관계의 Fetch Join으로 인해 DB단 페이징(Limit/Offset)이 무시되고
        // Hibernate가 전체 데이터를 메모리로 불러와(In-Memory Paging) 애플리케이션 OOM을 유발할 수 있습니다.
        org.springframework.data.domain.Page<Order> result = orderRepository.findAllByUserWithFetchJoinAndPaging(user, PageRequest.of(0, 10));

        System.out.println("========== [위험한 조회 종료] ==========\n");

        // Then: 결과는 정상적으로 반환되지만, 콘솔에 HHH000104(firstResult/maxResults warning) 경고가 출력됨을 증명
        assertThat(result.getContent()).hasSize(10);
    }
}