package com.project.eshop_refact.domain;

import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    @DisplayName("성공 : 재고 감소 로직 정상 동작")
    void removeStock(){
        //Given
        int initialStock = 10;
        Product product = new Product("Nike Air Force", 12000, initialStock);

        //When
        product.removeStock(3);

        //Then
        assertThat(product.getStockQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("실패 : 재고보다 많은 수량을 주문 -> 예외(Out_of_stock)")
    void removeStockFail(){
        //GIven
        int initialStock = 2;
        Product product = new Product("Limited Edition", 500000, initialStock);

        //Wehn & Then
        assertThatThrownBy(() -> product.removeStock(3))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
    }
}