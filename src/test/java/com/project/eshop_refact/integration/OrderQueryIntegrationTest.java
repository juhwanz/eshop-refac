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

    @Test
    @DisplayName("🚨 [위험] Fetch Join과 페이징 혼용 시 메모리 페이징 발생 증명")
    void checkMemoryPagingWarning() {
        System.out.println("\n========== [위험한 조회 시작: Fetch Join + Paging] ==========");

        // Given: setup()에서 생성한 유저 정보 가져오기
        User user = userRepository.findById(testUserId).orElseThrow();

        // When: [레거시] Fetch Join이 걸려있는 페이징 메서드 호출
        // PageSize를 10으로 주었지만, Hibernate는 다르게 동작합니다.
        org.springframework.data.domain.Page<Order> result =
                orderRepository.findAllByUserWithFetchJoinAndPaging(user, PageRequest.of(0, 10));

        System.out.println("========== [위험한 조회 종료] ==========\n");

        // Then: 결과 자체는 10개가 나오지만, 콘솔 로그에 치명적인 경고가 남아야 함
        assertThat(result.getContent()).hasSize(10);
    }
}