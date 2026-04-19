package com.project.eshop_refact.domain.product;

import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품(Product) 도메인 엔티티
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stockQuantity;

    public Product(String name, int price, int stockQuantity) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    /**
     * 재고 증가
     */
    public void addStock(int quantity) {
        this.stockQuantity += quantity;
    }

    /**
     * 재고 차감
     * 도메인 규칙에 따라 차감 후 잔여 재고가 0 미만이 될 경우 비즈니스 예외를 발생시킵니다.
     */
    public void removeStock(int quantity) {
        int restStock = this.stockQuantity - quantity;
        if (restStock < 0) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stockQuantity = restStock;
    }

    /**
     * 상품 가격 업데이트
     */
    public void updatePrice(int newPrice){
        if (newPrice < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        this.price = newPrice;
    }
}


