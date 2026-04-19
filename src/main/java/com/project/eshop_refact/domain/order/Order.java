package com.project.eshop_refact.domain.order;

import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문(Order) 도메인 엔티티
 */
@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // 연관관계 편의 메서드
    public void addOrderItem(OrderItem orderItem){
        orderItems.add(orderItem);
        orderItem.setOrder(this);
    }

    /**
     * 주문 생성 정적 팩토리 메서드
     * 연관관계 매핑 및 초기 상태 설정을 캡슐화합니다.
     */
    public static Order createOrder(User user, List<OrderItem> orderItems){
        Order order = new Order();
        order.user = user;
        for(OrderItem orderItem : orderItems){
            order.addOrderItem(orderItem);
        }
        order.status = OrderStatus.ORDER;

        return order;
    }


    /**
     * 주문 취소
     * 주문 상태를 검증한 후 취소 처리 및 하위 주문 상품의 재고를 복구합니다.
     */
    public void cancel(){
        if(this.status == OrderStatus.COMPLETED){
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_ORDER);
        }

        this.status = OrderStatus.CANCEL;

        for(OrderItem orderItem : orderItems){
            orderItem.getProduct().addStock(orderItem.getCount());
        }
    }
}

