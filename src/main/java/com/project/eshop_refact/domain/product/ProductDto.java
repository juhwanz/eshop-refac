package com.project.eshop_refact.domain.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * 상품 API 데이터 전송 객체(DTO)
 * 관련 DTO들을 Inner Static Class로 묶어 응집도를 높이고 클래스 파일 남발을 방지합니다.
 */
public class ProductDto {

    @Getter
    @Setter
    public static class RegisterRequest {

        @NotBlank(message = "상품명은 필수입니다.")
        private String name;

        @Min(value = 100, message = "가격은 최소 100원 이상")
        private int price;

        @Min(value = 1, message = "재고는 1개 이상이어야 합니다.")
        private int stockQuantity;

    }

    @Getter
    @Setter
    public static class SearchCondition {
        private String name;
        private Integer minPrice;
        private Integer maxPrice;

        /**
         * 클라이언트에서 넘어온 빈 문자열("")을 null로 치환하여,
         * 동적 쿼리(Dynamic Query) 생성 시 불필요한 검색 조건이 포함되는 것을 방지합니다.
         */
        public void setName(String name) {
            this.name = (name != null && name.trim().isEmpty()) ? null : name;
        }

        public boolean isValidPriceRange() {
            if (minPrice != null && maxPrice != null) {
                return minPrice <= maxPrice;
            }
            return true;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class Response {

        private Long id;
        private String name;
        private int price;
        private int stockQuantity;

        /**
         * Product 엔티티를 API 응답 스펙으로 변환하여, 엔티티의 변경이 클라이언트에 미치는 파급 효과를 차단합니다.
         */
        public Response(Product product) {
            this.id = product.getId();
            this.name = product.getName();
            this.price = product.getPrice();
            this.stockQuantity = product.getStockQuantity();
        }
    }

    @Getter
    @NoArgsConstructor
    public static class PriceUpdateRequest{
        @Min(value = 100, message = "가격은 최소 100원 이상이어야 합니다.")
        private int newPrice;
    }
}
