package com.project.eshop_refact.dto;

import com.project.eshop_refact.domain.Order;
import com.project.eshop_refact.domain.OrderItem;
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
    public static class Request {
        private Long productId;
        private int count;
    }

    @Getter
    @NoArgsConstructor
    public static class Response {

        private Long orderId;
        private String orderStatus; // 주문 상태
        private LocalDateTime orderDate;
        private List<OrderItemResponse> orderItems;

        public Response(Order order) {
            this.orderId = order.getId();
            this.orderStatus = order.getStatus().name();
            this.orderDate = order.getOrderDate();
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
            this.productName = orderItem.getProduct().getName();
            this.count = orderItem.getCount();
            this.orderPrice = orderItem.getOrderPrice();
        }
    }

}
