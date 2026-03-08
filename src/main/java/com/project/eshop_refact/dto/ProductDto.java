package com.project.eshop_refact.dto;

import com.project.eshop_refact.domain.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

// Cohesion: 상품 관련 DTO를 Inner Static Class로 그룹화하여 관리 효율성 증대
public class ProductDto {

    @Getter
    @Setter
    public static class RegisterRequest {

        // Validation: @Valid 어노테이션을 통한 입력값 검증 및 Fail-Fast 전략 구현
        @NotBlank(message = "상품명은 필수")
        private String name;

        // Domain Rule: 비즈니스 최소 가격 정책 반영
        @Min(value = 100, message = "가격은 최소 100원 이상")
        private int price;

        @Min(value = 0, message = "재고는 0개 이상이어야 함.")
        private int stockQuantity;

    }

    @Getter
    @Setter
    public static class SearchCondition {
        private String name;
        private Integer minPrice;
        private Integer maxPrice;
    }

    @Getter
    @NoArgsConstructor
    public static class Response {

        private Long id;
        private String name;
        private int price;
        private int stockQuantity;

        // Decoupling: 엔티티의 변경이 API 스펙에 영향을 주지 않도록 계층 분리
        // View Logic: 화면에 필요한 데이터만 선별하여 불필요한 정보 노출 방지
        public Response(Product product) {
            this.id = product.getId();
            this.name = product.getName();
            this.price = product.getPrice();
            this.stockQuantity = product.getStockQuantity();
        }
    }
}
