package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.Order;
import com.project.eshop_refact.domain.OrderItem;
import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.domain.User;
import com.project.eshop_refact.dto.OrderDto;
import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import com.project.eshop_refact.repository.OrderRepository;
import com.project.eshop_refact.repository.UserRepository;
import com.project.eshop_refact.service.strategy.StockStrategy;
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
     * 주문 생성
     * Strategy Pattern 적용: 재고 차감 방식을 런타임에 결정 (Pessimistic vs Facade)
     */
    @Transactional
    public Long order(Long userId, Long productId, int count){

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 재고 감소 (strategy 객체에 위임)
        Product product = stockStrategy.decrease(productId, count);

        OrderItem orderItem = OrderItem.createOrderItem(product, count);
        Order order = Order.createOrder(user, List.of(orderItem));

        orderRepository.save(order);
        return order.getId();
    }

    @Transactional(readOnly = true)
    public Page<OrderDto.Response> getOrders(Long userId, Pageable pageable){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Batch Size 설정을 통해 N+1 문제 없이 조회
        Page<Order> orderPage = orderRepository.findAllByUser(user, pageable);

        return orderPage.map(OrderDto.Response::new);
    }
}