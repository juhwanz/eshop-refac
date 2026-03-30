package com.project.eshop_refact.domain.order;

import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.order.strategy.StockStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final StockStrategy stockStrategy;

    /**
     * [Order Processing]
     * Atomic Transaction: 재고 차감과 주문 생성의 원자성 보장 (All or Nothing)
     * Strategy Pattern: 동시성 제어 로직을 위임하여 비즈니스 로직과 기술적 관심사(Locking) 분리
     */
    @Transactional
    public Long order(Long userId, Long productId, int count){

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 재고 감소 (strategy 객체에 위임)
        Product product = stockStrategy.decrease(productId, count);

        // DDD: 도메인 객체의 비즈니스 메서드를 통해 객체 생성 및 검증
        OrderItem orderItem = OrderItem.createOrderItem(product, count);
        Order order = Order.createOrder(user, List.of(orderItem));

        orderRepository.save(order);
        return order.getId();
    }

    //'default_batch_fetch_size' 설정을 통해 1:N 관계 조회 시 N+1 문제를 In-query(IN절)로 해결
    // Pagination Safety: 컬렉션 Fetch Join 시 발생하는 메모리 페이징(OutOfMemory) 이슈 원천 차단
    public Page<OrderDto.Response> getOrders(Long userId, Pageable pageable){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Batch Size 설정을 통해 N+1 문제 없이 조회
        Page<Order> orderPage = orderRepository.findAllByUser(user, pageable);

        return orderPage.map(OrderDto.Response::new);
    }

    // 락 흭득 전 어떤 상품에 락을 걸지 알아내기 위한 조회 메서드 : Redis 분산 락은 현재 상품 단위로 걸리게 설계 -> 취소 시 상품 ID를 동적으로 제공.
    public Long getProductIdByOrderId(Long orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow( () -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 첫 번째 상품 ID 반환
        return order.getOrderItems().get(0).getProduct().getId();
    }
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if(!order.getUser().getId().equals(userId)){
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }
        order.cancel(); // 도메인 로직 호출 (재고 복구)
    }
}