package com.project.eshop_refact.domain;

import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "products") // SQL문법 충돌 방지.
public class Product {
    // id, name, price, stockQuantity

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stockQuantity;

    // 상품 만들때 쓰는 생성자.
    public Product(String name, int price, int stockQuantity) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    // Domain-Driven Design : 도메인 로직을 엔티티안에 응집
    public void addStock(int quantity) {
        this.stockQuantity += quantity;
    }

    public void removeStock(int quantity) {
        int restStock = this.stockQuantity - quantity;
        if (restStock < 0) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stockQuantity = restStock;
    }

    public void updatePrice(int newPrice){
        if (newPrice < 0) {
            throw new IllegalArgumentException("가격은 0원 이상이어야 합니다.");
        }
        this.price = newPrice;
    }
}


