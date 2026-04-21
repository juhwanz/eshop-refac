package com.project.eshop_refact.domain;

import com.project.eshop_refact.domain.order.OrderItem;
import com.project.eshop_refact.domain.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderItem 도메인 단위 테스트
 * 주문 상품 생성 시점의 상품 가격 스냅샷 생성 및 객체 생성 유효성 검증 로직을 테스트합니다.
 */
public class OrderItemTest {

    @Test
    @DisplayName("성공 : 주문 상품 생성 시, 주문 가격 고정, 재고 변함 X.")
    void createOrderItem() {
        //Given
        Product product = new Product("셔츠", 100000, 100);
        int count = 5;

        //When
        OrderItem orderItem = OrderItem.createOrderItem(product, count);

        // Then
        assertThat(orderItem.getCount()).isEqualTo(count);
        assertThat(orderItem.getOrderPrice()).isEqualTo(100000);
        // OrderItem 객체 생성 자체는 Product의 상태(재고)를 변경하지 않음을 검증
        assertThat(product.getStockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("실패 : 주문 수량 0 이하, 상품 없으면 예외 발생")
    void createFail(){
        //Given
        Product product = new Product("셔츠", 100000, 100);

        //When & Then
        assertThatThrownBy(() -> OrderItem.createOrderItem(product, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 수량은 1개 이상이어야 합니다.");

        assertThatThrownBy(() -> OrderItem.createOrderItem(null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문할 상품 정보가 없습니다.");
    }
}
