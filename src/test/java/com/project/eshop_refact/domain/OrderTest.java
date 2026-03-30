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

        // 서비스 로직 흉내냄.
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
        User user = new User("test@test.com", "1234", "tester", UserRoleEnum.USER);
        Product product = new Product("신발", 10000, 100);
        OrderItem orderItem = OrderItem.createOrderItem(product, 1);
        Order order = Order.createOrder(user, List.of(orderItem));

        // ReflectionTestUtil을 사용해 COMPLETED 상태
        ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETED);

        // WHEN & THEN
        assertThatThrownBy(order::cancel)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_CANCEL_ORDER);
    }
}
