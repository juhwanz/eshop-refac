package com.project.eshop_refact.domain.queue;

import com.project.eshop_refact.global.common.ApiResponse;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products/{productId}/queue")
public class QueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping
    public ResponseEntity<ApiResponse<QueueDto.Response>> register(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        WaitingQueueService.Registration registration = waitingQueueService.register(
                productId,
                userDetails.getUser().getId()
        );
        HttpStatus status = registration.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponse.success("대기열 등록 성공", registration.response()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<QueueDto.Response>> getStatus(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        QueueDto.Response response = waitingQueueService.getStatus(productId, userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("대기열 상태 조회 성공", response));
    }
}
