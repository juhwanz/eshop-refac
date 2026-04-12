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
 *  [Order Domain]
 *  무분별한 Setter 지양, 비즈니스 로직을 도메인 내부 캡슐화 : Rich Domain Model
 */

@Entity
@Getter
@NoArgsConstructor  // JPA 리플렉션 + 프록시 객체 생성을 위한 기본 생성자.
@EntityListeners(AuditingEntityListener.class) // Auditing 기능(생성/수정 시간 자동화) 활성화.
@Table(name = "orders") // DB 예약어(Order) 충돌 방지
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // N+1 문제 방지를 위해 Lazy Loading
    // 주문할 회원 (N : 1) | 주문 - User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Order 엔티티가 하위 OrderItem의 생명주기 전적 관리(CascadeType.ALL)
    // 주문할 상품들 ( 1 : N ) | cascade - 주문서 삭제 시, 딸린 주문들도 같이 삭제.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @CreatedDate // 저장 시 시간 자동 삽입
    @Column(updatable = false)              // 주문 시간은 생성 이후 불변성 보장
    private LocalDateTime orderDate;        // 주문 시간

    // Enum 순서 변경으로 인한 치명적인 DB 데이터 오염을 막기 위해 STRING 타입 명시
    @Enumerated(EnumType.STRING)
    private OrderStatus status;             // 주문 상태

    // 연관관계 편의 메서드
    public void addOrderItem(OrderItem orderItem){
        orderItems.add(orderItem);
        orderItem.setOrder(this);
    }

    /**
     * [Static Factory Method] 주문 생성 로직 캡슐화
     * 도메인 생성에 필요한 검증 및 상태 초기화 로직 응집
     * 외부(서비스 계층)에서 불완전한 상태의 객체가 생성되는 것 원천 차단.
     */
    public static Order createOrder(User user, List<OrderItem> orderItems){
        Order order = new Order();
        order.user = user;
        for(OrderItem orderItem : orderItems){
            order.addOrderItem(orderItem);      // 연관관계 편의 메서드
        }
        order.status = OrderStatus.ORDER;       // 초기 상태 강제

        return order;
    }


    /**
     * [도메인 비즈니스 로직] 주문 취소 및 재고 롤백
     * 서비스(Service) 계층에 로직을 노출하지 않고, 도메인 스스로 상태를 검증하고 변경하도록 설계했습니다.
     */
    public void cancel(){
        // 도메인 상태 변경 전 비즈니스 규칙(이미 완료된 주문은 취소 불가) 검증
        if(this.status == OrderStatus.COMPLETED){
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_ORDER);
        }

        this.status = OrderStatus.CANCEL;

        // OrderItem를 통해 Product 도메인에 재고 복구 메시지 전달
        for(OrderItem orderItem : orderItems){
            orderItem.getProduct().addStock(orderItem.getCount());
        }
    }
}

