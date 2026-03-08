package com.project.eshop_refact.domain;

import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
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
@NoArgsConstructor  // access = AccessLevel.PROTECTED 테스트 때문에 풀어 둠, JPA 구현체의 리플렉션(Reflection) 사용을 위한 기본 생성자
@EntityListeners(AuditingEntityListener.class) // Auditing 기능 활성화.
@Table(name = "orders") // DB 예약어(Order) 충돌 방지
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // N+1 문제 방지를 위해 지연 로딩(Lazy Loading)을 기본 전략으로 채택
    // 주문할 회원 (N : 1) | 주문 - User
    @ManyToOne(fetch = FetchType.LAZY)      //주문 정보만 필요하면 주문 정보만. ( 성능 최적화 )
    @JoinColumn(name = "user_id")
    private User user;

    // 영속성 전이(Cascade): Order 삭제 시 연관된 OrderItem의 생명주기 동기화
    // 주문할 상품들 ( 1 : N ) | cascade - 주문서 삭제 시, 딸린 주문들도 같이 삭제.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @CreatedDate // 저장 시 시간 자동 삽입
    @Column(updatable = false)
    private LocalDateTime orderDate;        // 주문 시간

    // 데이터 무결성 및 확장성을 위해 EnumType.STRING 사용
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

    // 도메인 주도 설계(DDD): 비즈니스 로직을 엔티티 내부에 응집시켜 객체지향적 설계 구현
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

