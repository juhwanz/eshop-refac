package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.*;
import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import com.project.eshop_refact.repository.OrderRepository;
import com.project.eshop_refact.repository.UserRepository;
import com.project.eshop_refact.service.strategy.StockStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// DB 없이 Mock을 써서 흐름만 빠르게 검증.
@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @InjectMocks
    private OrderService orderService; // 테스트 대상

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StockStrategy stockStrategy;

    @Test
    @DisplayName(" 주문 성공시, 재고 감소 요청 -> 주문 저장.")
    void orderSuccess(){
        //Given
        Long userId = 1L;
        Long productId = 100L;
        int count = 2;

        User user = new User("user@test.com", "pw", "user", UserRoleEnum.USER);
        Product product = new Product("item", 10000, 10);

        //Mocking : 가짜 행동 정의
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        // ProductService가 호출되면 재고가 줄어든 상품을 반환한다고 가정
        given(stockStrategy.decrease(productId, count)).willReturn(product);

        //When
        orderService.order(userId, productId, count);

        //Then
        verify(stockStrategy).decrease(productId, count);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 실패 : 존재하지 않은 유저 -> 예외 발생")
    void orderFail(){
        //Given
        Long userId = 999L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // When & Tehn
        assertThatThrownBy(() -> orderService.order(userId, 1L, 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
