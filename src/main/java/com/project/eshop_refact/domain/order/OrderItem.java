package com.project.eshop_refact.domain.order;

import com.project.eshop_refact.domain.product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 상품(OrderItem) 도메인 엔티티
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    private int orderPrice;
    private int count;

    /**
     * 주문 상품 생성 정적 팩토리 메서드
     * 도메인 생성 시점에 필수 값을 검증하고 안전하게 초기화합니다.
     */
    public static OrderItem createOrderItem(Product product, int count){
        if(product == null){
            throw new IllegalArgumentException("주문할 상품 정보가 없습니다.");
        }
        if(count <= 0){
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }
        OrderItem orderItem = new OrderItem();

        orderItem.product = product;
        orderItem.count = count;
        orderItem.orderPrice = product.getPrice();

        return orderItem;
    }

    // 연관관계 편의 메서드 지원용 (Order 엔티티 내부에서만 접근 허용)
    protected void setOrder(Order order) {
        this.order = order;
    }

}

