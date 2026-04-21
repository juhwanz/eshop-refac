package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.order.*;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.order.strategy.StockStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * OrderService 비즈니스 로직 단위 테스트
 * 데이터베이스 연동 없이 Mockito를 활용하여 서비스 계층의 순수 도메인 흐름과 예외 처리,
 * 그리고 의존 객체(StockStrategy 등)와의 상호작용을 격리하여 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StockStrategy stockStrategy;

    @Test
    @DisplayName("주문 생성 성공: 재고 차감 전략(Strategy) 수행 후 주문 정보가 저장된다")
    void orderSuccess(){
        //Given
        Long userId = 1L;
        Long productId = 100L;
        int count = 2;

        User user = new User("user@test.com", "pw", "user", UserRoleEnum.USER);
        Product product = new Product("item", 10000, 10);


        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // 동시성 제어 전략 객체(StockStrategy)가 정상적으로 재고를 차감한 상품을 반환하도록 Stubbing
        given(stockStrategy.decrease(productId, count)).willReturn(product);

        //When
        orderService.order(userId, productId, count);

        //Then
        verify(stockStrategy).decrease(productId, count);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 실패: 존재하지 않는 유저 요청 시 비즈니스 예외가 발생한다")
    void orderFail(){
        //Given
        Long userId = 999L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.order(userId, 1L, 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 실패: 재고 부족 시 전략 객체(StockStrategy)의 예외가 정상적으로 전파된다")
    void orderFail_outOfStock() {
        // Given
        Long userId = 1L;
        Long productId = 100L;
        int count = 20;

        User user = new User("user@test.com", "pw", "user", UserRoleEnum.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // 전략 객체(Lock)에서 재고 부족 예외가 터진다고 가정
        given(stockStrategy.decrease(productId, count))
                .willThrow(new BusinessException(ErrorCode.OUT_OF_STOCK));

        // When & Then
        assertThatThrownBy(() -> orderService.order(userId, productId, count))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("주문 목록 조회 성공")
    void getOrders_success() {
        // Given
        Long userId = 1L;
        User user = new User("user@test.com", "pw", "user", UserRoleEnum.USER);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        Product product = new Product("item", 10000, 10);
        OrderItem orderItem = OrderItem.createOrderItem(product, 1);
        Order order = Order.createOrder(user, List.of(orderItem));

        ReflectionTestUtils.setField(order, "id", 10L);
        Page<Order> expectedPage = new PageImpl<>(List.of(order));

        given(orderRepository.findAllByUser(any(User.class), any())).willReturn(expectedPage);

        // When
        Page<OrderDto.Response> result = orderService.getOrders(userId, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getOrderId()).isEqualTo(10L);
    }


    @Test
    @DisplayName("주문 취소 성공: 주문 상태가 CANCEL로 전이되고 연관된 상품의 재고가 롤백된다")
    void cancelOrder_success() {
        // Given
        Product product = new Product("item", 10000, 5);
        User user = new User("test", "pw", "user", UserRoleEnum.USER);
        ReflectionTestUtils.setField(user, "id", 1L);

        // 주문 생성 시 서비스 계층에서 수행되는 재고 차감 상황을 도메인 레벨에서 사전 구성
        product.removeStock(2);

        OrderItem orderItem = OrderItem.createOrderItem(product, 2);
        Order order = Order.createOrder(user, List.of(orderItem));

        assertThat(product.getStockQuantity()).isEqualTo(3);

        given(orderRepository.findById(10L)).willReturn(Optional.of(order));

        // When
        orderService.cancelOrder(10L, 1L);

        // Then
        // 취소 로직 내부에서 도메인 메서드(addStock)가 호출되어 재고가 정상 복구되었는지 확인
        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL);
    }

    @Test
    @DisplayName("주문 취소 실패 : 존재하지 않는 주문")
    void cancelOrder_fail_notFound() {
        // Given
        Long orderId = 999L;
        Long userId = 1L;
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(orderId,userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 취소 실패: 타인의 주문을 취소하려 할 경우 권한 예외(FORBIDDEN)가 발생한다")
    void cancelOrder_fail_forbidden() {
        // Given
        Long orderId = 10L;
        Long ownerId = 1L;
        Long requesterId = 2L;

        User owner = new User("owner@test.com", "pw", "owner", UserRoleEnum.USER);
        ReflectionTestUtils.setField(owner, "id", ownerId);

        Order order = new Order();
        ReflectionTestUtils.setField(order, "user", owner);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // When & Then: 타인(2L)이 취소 요청 시 예외 발생 확인
        assertThatThrownBy(() -> orderService.cancelOrder(orderId, requesterId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_ACCESS);
    }
}
