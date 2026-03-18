package com.project.eshop_refact.controller;

import com.project.eshop_refact.config.UserDetailsImpl;
import com.project.eshop_refact.dto.ApiResponse;
import com.project.eshop_refact.service.queue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders/queue")
@Profile({"dev", "test", "local"}) // 테스트 환경에서만
public class TestSupportController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> registerQueue(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long rank = waitingQueueService.registerQueue(userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("대기열 등록 성공", rank));
    }
}
