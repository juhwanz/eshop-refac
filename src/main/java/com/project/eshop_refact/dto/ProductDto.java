package com.project.eshop_refact.dto;

import com.project.eshop_refact.domain.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class ProductDto {
    @Getter
    @Setter
    public static class RegisterRequest {

        @NotBlank(message = "상품명은 필수")
        private String name;

        @Min(value = 100, message = "가격은 최소 100원 이상")
        private int price;

        @Min(value = 0, message = "재고는 0개 이상이어야 함.")
        private int stockQuantity;

    }

    @Data
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

        public Response(Product product) {
            this.id = product.getId();
            this.name = product.getName();
            this.price = product.getPrice();
            this.stockQuantity = product.getStockQuantity();
        }
    }
}
