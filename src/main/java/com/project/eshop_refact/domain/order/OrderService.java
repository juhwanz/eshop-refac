package com.project.eshop_refact.domain.order;

import com.project.eshop_refact.domain.order.strategy.PessimisticLockStrategy;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductCacheEvictEvent;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.order.strategy.StockStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final StockStrategy stockStrategy;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 주문 생성
     * 동시성 제어(Locking) 책임을 Strategy 계층에 위임하여 비즈니스 로직과 기술적 관심사를 분리합니다.
     */
    @Transactional
    public Long order(Long userId, Long productId, int count){
        return order(userId, productId, count, null);
    }

    public Optional<Long> findCompletedOrder(Long userId, OrderRequestIdentity identity) {
        return orderRepository.findByUserIdAndIdempotencyKey(userId, identity.keyBytes())
                .map(order -> {
                    identity.verifyFingerprint(order.getRequestFingerprint());
                    return order.getId();
                });
    }

    @Transactional
    public Long order(Long userId, Long productId, int count, OrderRequestIdentity identity) {
        if (identity != null) {
            Optional<Long> completed = findCompletedOrder(userId, identity);
            if (completed.isPresent()) {
                return completed.get();
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = stockStrategy.decrease(productId, count);

        OrderItem orderItem = OrderItem.createOrderItem(product, count);
        Order order = Order.createOrder(user, List.of(orderItem));

        if (identity != null) {
            order.identifyRequest(identity);
        }
        orderRepository.save(order);
        if (identity != null) {
            orderRepository.flush();
        }
        return order.getId();
    }

    /**
     * 주문 목록 조회
     * default_batch_fetch_size 설정을 활용하여 1:N 관계 조회 시 발생하는 N+1 문제를 방지하고,
     * 컬렉션 Fetch Join 페이징 시 발생할 수 있는 메모리 부하(OOM)를 구조적으로 회피합니다.
     */
    public Page<OrderDto.Response> getOrders(Long userId, Pageable pageable){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Page<Order> orderPage = orderRepository.findAllByUser(user, pageable);

        return orderPage.map(OrderDto.Response::new);
    }

    /**
     * 주문 취소 시 분산 락 획득 기준이 되는 상품 ID 조회
     */
    public Long getProductIdByOrderId(Long orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow( () -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        return order.getOrderItems().get(0).getProduct().getId();
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if(!order.getUser().getId().equals(userId)){
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        if (order.isCancelled()) {
            return;
        }

        order.cancel();

        // 재고 복구 후, 변경된 상품의 캐시를 무효화하기 위해 이벤트를 발행합니다.
        order.getOrderItems().forEach(orderItem ->
                eventPublisher.publishEvent(new ProductCacheEvictEvent(orderItem.getProduct().getId()))
        );
    }
}
