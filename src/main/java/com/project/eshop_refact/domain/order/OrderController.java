package com.project.eshop_refact.domain.order;

import com.project.eshop_refact.global.security.UserDetailsImpl;
import com.project.eshop_refact.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final RedissonLockStockFacade redissonLockStockFacade;
    private final OrderService orderService;
    private final OrderIdempotencyService orderIdempotencyService;

    /**
     * 주문 생성 API
     * HTTP 헤더의 Idempotency-Key를 활용하여 클라이언트의 재시도로 인한 중복 주문 결제를 방지합니다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto.CreateResponse>> createOrder(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid OrderDto.CreateRequest requestDto,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    )throws Exception{
        Long userId = userDetails.getUser().getId();
        OrderDto.CreateResponse response = orderIdempotencyService.processOrderWithIdempotency(
                idempotencyKey,
                userId,
                requestDto.getProductId(),
                requestDto.getCount()
        );

        return ResponseEntity.status(201).body(ApiResponse.success("주문 성공", response));
    }


    /**
     * 주문 목록 조회 API
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderDto.Response>>> getOrders(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)Pageable pageable
    ){
        Long userId = userDetails.getUser().getId();
        Page<OrderDto.Response> orders = orderService.getOrders(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("주문 목록 조회 성공", orders));
    }

    /**
     * 주문 취소 API
     * 동시성 환경에서 안전한 재고 복구를 위해 Redisson 기반의 분산 락 파사드를 호출합니다.
     */
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ){
        Long userId = userDetails.getUser().getId();
        redissonLockStockFacade.cancelOrder(orderId, userId);
        return ResponseEntity.ok(ApiResponse.success("주문 취소 성공"));
    }
}
