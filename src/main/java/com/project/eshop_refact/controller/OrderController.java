package com.project.eshop_refact.controller;

import com.project.eshop_refact.config.UserDetailsImpl;
import com.project.eshop_refact.dto.OrderDto;
import com.project.eshop_refact.facade.RedissonLockStockFacade;
import com.project.eshop_refact.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    // private final WaitingQueueService waitingQueueService; // [Security] 운영 배포 시 제거 권장

    @PostMapping
    public ResponseEntity<Long> createOrder(
            @RequestBody OrderDto.Request requestDto,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ){
        Long userId = userDetails.getUser().getId();

        //Long orderId = redissonLockStockFacade.order(userId, requestDto.getProductId(), requestDto.getCount());
        // Facade를 통해 "분산 락 -> 트랜잭션 -> 재고 차감" 순차 실행
        Long orderId = redissonLockStockFacade.order(
                userId,
                requestDto.getProductId(),
                requestDto.getCount()
        );

        return ResponseEntity.ok(orderId);
    }


    @GetMapping
    public ResponseEntity<Page<OrderDto.Response>> getOrders(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)Pageable pageable
    ){
        Long userId = userDetails.getUser().getId();
        // 단순 조회는 락이 없으니 service 바로 호출.
        Page<OrderDto.Response> orders = orderService.getOrders(userId, pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * 대기열 등록 API (테스트용)
     * POST /api/orders/queue
     */
    /*@PostMapping("/queue")
    public Long registerQueue(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return waitingQueueService.registerQueue(userDetails.getUser().getId());}
    */
}
