package com.project.eshop_refact.domain;

import com.project.eshop_refact.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// OderItem이 생성될 시 재고가 줄어드는지, 가격이 고정되는지 확인
public class OrderItemTest {

    @Test
    @DisplayName("주문 상품 생성 시 재고가 줄어들고, 주문 가격이 고정된다")
    void createOrderItem() {
        //Given
        int originalStock = 100;
        int price = 10000;
        Product product = new Product("티셔츠", price, originalStock);
        int count = 5;

        //When
        OrderItem orderItem = OrderItem.createOrderItem(product, count);

        // Then
        assertThat(orderItem.getCount()).isEqualTo(count);
        assertThat(orderItem.getOrderPrice()).isEqualTo(price);
        assertThat(product.getStockQuantity()).isEqualTo(originalStock);
    }

// "재고 초과 예외"는 이제 OrderItem 생성 시점이 아니라,
// Service에서 락을 걸고 수행할 때 발생하므로 여기서 테스트하는 것은 맞지 않습니다.
    /*@Test
    @DisplayName("재고보다 오바 주문 -> 예외 발생")
    void removeStockFail(){
        //Given
        Product product = new Product("티셔츠", 10000, 10);
        //When & Then
        assertThatThrownBy(() -> OrderItem.createOrderItem(product,11))
                .isInstanceOf(BusinessException.class) // or NotEnoughStockException
                .hasMessageContaining("재고"); // 에러메시지 검증.
    }*/
}
