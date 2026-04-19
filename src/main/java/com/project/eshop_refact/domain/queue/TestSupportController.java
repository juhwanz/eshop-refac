package com.project.eshop_refact.domain.queue;

import com.project.eshop_refact.global.security.UserDetailsImpl;
import com.project.eshop_refact.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열 테스트 지원용 API 컨트롤러
 * 부하 테스트 및 프론트엔드 연동 테스트 시 대기열 진입을 수동으로 제어하기 위해 사용합니다.
 * 보안 및 데이터 정합성을 위해 운영(prod) 환경에서는 로드되지 않도록 격리되어 있습니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders/queue")
@Profile({"dev", "test", "local"}) // 테스트 환경에서만
public class TestSupportController {

    private final WaitingQueueService waitingQueueService;

    /**
     * 대기열 수동 등록 API
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> registerQueue(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long rank = waitingQueueService.registerQueue(userDetails.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("대기열 등록 성공", rank));
    }
}
