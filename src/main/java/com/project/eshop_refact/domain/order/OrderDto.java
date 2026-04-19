package com.project.eshop_refact.domain.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class OrderDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "상품 ID는 필수입니다.")
        private Long productId;

        // 최소 주문 수량 검증
        @Min(value = 1, message = "주문 수량은 최소 1개 이상이어야 합니다.")
        private int count;
    }

    @Getter
    @NoArgsConstructor
    public static class Response {

        private Long orderId;
        private String orderStatus;
        private LocalDateTime orderDate;
        private List<OrderItemResponse> orderItems;

        /**
         * 엔티티 직접 노출을 방지하여 도메인을 보호하고 API 스펙을 유지합니다.
         */
        public Response(Order order) {
            this.orderId = order.getId();
            this.orderStatus = order.getStatus().name();
            this.orderDate = order.getOrderDate();
            // Infinite Recursion: 양방향 연관관계로 인한 JSON 직렬화 무한 루프 방지
            this.orderItems = order.getOrderItems().stream()
                    .map(OrderItemResponse::new)
                    .collect(Collectors.toList());
        }
    }

    @Getter
    @NoArgsConstructor
    public static class OrderItemResponse {
        private String productName;
        private int count;
        private int orderPrice;

        public OrderItemResponse(OrderItem orderItem) {
            // 주의: N+1 문제를 방지하기 위해 상위 계층(Repository)에서 Product 엔티티를 Fetch Join으로 미리 로드해야 합니다.
            this.productName = orderItem.getProduct().getName();
            this.count = orderItem.getCount();
            this.orderPrice = orderItem.getOrderPrice();
        }
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreateResponse {
        private Long orderId;
    }
}
