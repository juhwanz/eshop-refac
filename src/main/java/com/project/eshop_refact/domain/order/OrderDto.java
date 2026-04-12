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

        // Validation: 비즈니스 규칙(최소 수량)에 따른 입력값 검증 및 Fail-Fast 적용
        @Min(value = 1, message = "주문 수량은 최소 1개 이상이어야 합니다.")
        private int count;
    }

    @Getter
    @NoArgsConstructor
    public static class Response {

        private Long orderId;
        private String orderStatus; // 주문 상태
        private LocalDateTime orderDate;
        private List<OrderItemResponse> orderItems;

        // Decoupling: 엔티티를 직접 노출하지 않고 DTO로 변환 -> API 스펙 변경 영향 최소화
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
            // 지연 로딩 객체 접근 시 N+1 문제 발생 가능 -> Repository 계층에서 Fetch Join을 통해 Product 엔티티를 미리 로드해야 함
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
