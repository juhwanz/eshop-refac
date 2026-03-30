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

// DB 없이 Mock을 써서 흐름만 빠르게 검증.
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

        // When & Then
        assertThatThrownBy(() -> orderService.order(userId, 1L, 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 실패 : 재고 부족 시 예외 발생 (StockStrategy 예외 전파)")
    void orderFail_outOfStock() {
        // Given
        Long userId = 1L;
        Long productId = 100L;
        int count = 20; // 재고를 초과하는 수량

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

        // [수정] 필드가 채워진 정상적인 Order 객체 생성
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
    @DisplayName("주문 취소 성공 (도메인 로직 호출 검증)")
    void cancelOrder_success() {
        // [1] Given: 최초 재고 5개인 상품 준비
        Product product = new Product("item", 10000, 5);
        User user = new User("test", "pw", "user", UserRoleEnum.USER);
        ReflectionTestUtils.setField(user, "id", 1L);

        // [2] 주문 생성
        // 현재 OrderItem 생성 시 재고를 안 줄이므로, 테스트를 위해 수동으로 줄여줍니다.
        product.removeStock(2); // <--- 테스트 환경에 맞게 수동 차감 추가 (5 -> 3)

        OrderItem orderItem = OrderItem.createOrderItem(product, 2);
        Order order = Order.createOrder(user, List.of(orderItem));

        // [검증 1] 취소 전 재고가 3인지 확인
        assertThat(product.getStockQuantity()).isEqualTo(3);

        given(orderRepository.findById(10L)).willReturn(Optional.of(order));

        // [3] When: 주문 취소 실행 (내부에서 orderItem.getProduct().addStock(2) 호출됨)
        orderService.cancelOrder(10L, 1L);

        // [4] Then: 취소 후 재고가 다시 5인지 확인
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
    @DisplayName("주문 취소 실패 : 권한 없는 유저 (타인의 주문 취소 시도)")
    void cancelOrder_fail_forbidden() {
        // Given
        Long orderId = 10L;
        Long ownerId = 1L;
        Long requesterId = 2L; // 요청자는 타인

        User owner = new User("owner@test.com", "pw", "owner", UserRoleEnum.USER);
        ReflectionTestUtils.setField(owner, "id", ownerId);

        Order order = new Order();
        ReflectionTestUtils.setField(order, "user", owner); // 주문의 소유자는 ownerId (1L)

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // When & Then: 타인(2L)이 취소 요청 시 예외 발생 확인
        assertThatThrownBy(() -> orderService.cancelOrder(orderId, requesterId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN_ACCESS);
    }
}
