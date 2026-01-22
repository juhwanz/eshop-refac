package com.project.eshop_refact.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor// access = AccessLevel.PROTECTED 테스트 때문에 풀어 둠
@EntityListeners(AuditingEntityListener.class) // Auditing 기능 활성화.
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주문할 회원 (N : 1) | 주문 - User
    @ManyToOne(fetch = FetchType.LAZY)      //주문 정보만 필요하면 주문 정보만. ( 성능 최적화 )
    @JoinColumn(name = "user_id")
    private User user;

    // 주문할 상품들 ( 1 : N ) | cascade - 주문서 삭제 시, 딸린 주문들도 같이 삭제.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @CreatedDate // 저장 시 시간 자동 삽입
    @Column(updatable = false)
    private LocalDateTime orderDate;        // 주문 시간

    @Enumerated(EnumType.STRING)
    private OrderStatus status;             // 주문 상태

    // 연관관계 편의 메서드
    public void addOrderItem(OrderItem orderItem){
        orderItems.add(orderItem);
        orderItem.setOrder(this);
    }

    // 생성 메서드 (Factory Method)
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
     * 비즈니스 로직: 주문 취소
     * 배송 완료 상태에서는 취소가 불가능하다는 도메인 규칙을 포함함.
     */
    public void cancel(){
        if(this.status == OrderStatus.COMPLETED){
            throw new IllegalStateException("이미 완료된 주문은 취소가 불가능합니다.");
        }

        this.status = OrderStatus.CANCEL;
    }
}

