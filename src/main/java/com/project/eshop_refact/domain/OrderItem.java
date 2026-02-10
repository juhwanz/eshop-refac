package com.project.eshop_refact.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter // @Setter 어디서든 값 바꿀수 있었으나, 생성할 때 한번 생성으로 교체.
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본생성자 JPA 스펙상 필요, 외부에서 못 쓰게 protected로 방어.
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 상품을 샀나? ( N : 1 )
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // 어느 주문서에 속함? ( N : 1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    private int orderPrice;
    private int count;

    public static OrderItem createOrderItem(Product product, int count){
        OrderItem orderItem = new OrderItem();
        // Setter 대신 내부 필드에 직접 할당
        orderItem.product = product;
        orderItem.count = count;
        orderItem.orderPrice = product.getPrice();

        //product.removeStock(count);// 삭제함 (OrderService의 StockStrategy가 이미 처리했음)

        return orderItem;
    }

    // 연관관계 메서드 : Order엔티티에서만 호출할 수 있게 protected로
    protected void setOrder(Order order) {
        this.order = order;
    }

}

