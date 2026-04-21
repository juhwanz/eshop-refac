package com.project.eshop_refact.domain;

import com.project.eshop_refact.domain.order.Order;
import com.project.eshop_refact.domain.order.OrderItem;
import com.project.eshop_refact.domain.order.OrderStatus;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Order 도메인 단위 테스트
 * 애그리거트 루트(Aggregate Root)인 주문(Order) 객체의 생성, 상태 전이(취소),
 * 그리고 연관 객체(OrderItem, Product) 간의 양방향 매핑 및 비즈니스 규칙을 독립적으로 검증합니다.
 */
public class OrderTest {

    @Test
    @DisplayName("주문 생성 시, 초기 상태는 ORDER, 양방향 연결 되어야 함.")
    void creatOrder() {
        // Given
        User user = new User("test@test.com", "1234", "tester", UserRoleEnum.USER);
        Product product = new Product("신발", 10000, 100);
        OrderItem orderItem = OrderItem.createOrderItem(product, 2);

        //When
        Order order = Order.createOrder(user, List.of(orderItem));

        //Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDER);
        assertThat(order.getOrderItems()).hasSize(1);
        assertThat(order.getUser()).isEqualTo(user);
        assertThat(orderItem.getOrder()).isEqualTo(order);
    }

    @Test
    @DisplayName("주문을 취소하면 상태가 CANCEL로 변경 및 재고 롤백.")
    void cancelOrder() {
        // Given
        User user = new User("test@test.com", "1234", "tester", UserRoleEnum.USER);
        Product product = new Product("신발", 10000, 100);

        // 주문 생성 시 서비스 계층에서 수행되는 재고 차감 상황을 도메인 레벨에서 가정하여 사전 구성
        product.removeStock(2);
        OrderItem orderItem = OrderItem.createOrderItem(product, 2);
        Order order = Order.createOrder(user, List.of(orderItem));

        //When
        order.cancel();

        //Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL);
        assertThat(product.getStockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("실패 : 이미 배송완료된(COMPLETED) 주문은 취소할 수 없다")
    void cancelFail(){
        // Given
        User user = new User("test@test.com", "1234", "tester", UserRoleEnum.USER);
        Product product = new Product("신발", 10000, 100);
        OrderItem orderItem = OrderItem.createOrderItem(product, 1);
        Order order = Order.createOrder(user, List.of(orderItem));

        // 도메인 외부에서 임의로 상태를 변경할 수 없도록 캡슐화되어 있으므로,
        // 예외 케이스 검증을 위해 리플렉션을 통해 강제로 COMPLETED 상태를 주입
        ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETED);

        // WHEN & THEN
        assertThatThrownBy(order::cancel)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_CANCEL_ORDER);
    }
}
