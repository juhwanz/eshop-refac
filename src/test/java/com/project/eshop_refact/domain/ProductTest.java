package com.project.eshop_refact.domain;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Product 도메인 단위 테스트
 * 상품(Product) 엔티티 자체의 핵심 비즈니스 로직(재고 증감, 가격 수정)과 예외 검증을
 * 외부 프레임워크 의존성 없이 독립적으로 테스트합니다.
 */
class ProductTest {

    @Test
    @DisplayName("성공 : 재고 추가 로직.")
    void addStock(){
        //Given
        Product product = new Product("AIr-FOrce", 10000, 10);

        //WHEN
        product.addStock(10);

        //Then
        assertThat(product.getStockQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("성공 : 재고 감소 로직 정상 동작")
    void removeStock(){
        //Given
        Product product = new Product("Nike", 12000, 10);

        //When
        product.removeStock(3);

        //Then
        assertThat(product.getStockQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("실패 : 재고보다 많은 수량을 주문 -> 예외(Out_of_stock)")
    void removeStockFail(){
        //GIven

        Product product = new Product("Limited Edition", 500000, 2);

        //Wehn & Then
        assertThatThrownBy(() -> product.removeStock(3))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("성공 : 가격 수정 정상 동작")
    void updatePrice_success() {
        // Given
        Product product = new Product("Nike", 10000, 10);

        // When
        product.updatePrice(15000);

        // Then
        assertThat(product.getPrice()).isEqualTo(15000);
    }

    @Test
    @DisplayName("실패 : 음수 가격으로 수정 시 예외 발생")
    void updatePrice_fail() {
        // Given
        Product product = new Product("Nike", 10000, 10);

        // When & Then
        assertThatThrownBy(() -> product.updatePrice(-100))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }
}