package com.project.eshop_refact.domain.product;

import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor  // JPA 리플렉션(Reflection) 지원
@Table(name = "products") // DB 예약어 충돌 방지
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

    // 생성 시점부터 유효한 상태를 보장하기 위해 생성자 주입 강제 -> 무분별한 setter 방지.
    public Product(String name, int price, int stockQuantity) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    // DDD (Rich Domain Model): 데이터와 로직을 응집시켜 캡슐화(Encapsulation) 강화
    // Setter를 닫고 비즈니스 의도가 명확한 메서드를 통해서만 상태 변경 허용
    public void addStock(int quantity) {
        this.stockQuantity += quantity;
    }

    // Validation: 도메인 규칙(재고 < 0 불가) 검증을 엔티티 내부에서 수행하여 무결성 보호
    public void removeStock(int quantity) {
        int restStock = this.stockQuantity - quantity;
        if (restStock < 0) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.stockQuantity = restStock;
    }

    public void updatePrice(int newPrice){
        if (newPrice < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        this.price = newPrice;
    }
}


