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
@RequiredArgsConstructor        // 생성자 주입으로 통일 (Lombok)
@RequestMapping("/api/orders")
public class OrderController {

    private final RedissonLockStockFacade redissonLockStockFacade;
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto.CreateResponse>> createOrder(
            @RequestBody @Valid OrderDto.CreateRequest requestDto,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ){
        Long userId = userDetails.getUser().getId();
        // Facade를 통해 "분산 락 -> 트랜잭션 -> 재고 차감" 순차 실행
        Long orderId = redissonLockStockFacade.order(
                userId,
                requestDto.getProductId(),
                requestDto.getCount()
        );
        return ResponseEntity.status(201).body(ApiResponse.success("주문 성공", new OrderDto.CreateResponse(orderId)));
    }


    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderDto.Response>>> getOrders(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)Pageable pageable
    ){
        Long userId = userDetails.getUser().getId();
        // 단순 조회는 락이 없으니 service 바로 호출.
        Page<OrderDto.Response> orders = orderService.getOrders(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("주문 목록 조회 성공", orders));
    }

    // 취소 로직
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
