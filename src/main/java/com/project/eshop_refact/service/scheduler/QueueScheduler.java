package com.project.eshop_refact.service.scheduler;

import com.project.eshop_refact.service.queue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final WaitingQueueService waitingQueueService;

    // Capacity Planning: 부하 테스트(nGrinder)를 통해 산출된 TPS 한계치 반영
    // Rate Limiting: DB Connection Pool 고갈 방지를 위한 진입 허용량 제한
    private static final long ENTER_COUNT = 100L;

    // Flow Control: 주기적인 유입량 제어를 통해 시스템 과부하 방지 (Backpressure 관리)
    // Safety: fixedDelay를 사용하여 이전 작업 지연 시 스케줄링 간격을 자동 조정 (Graceful Degradation)
    @Scheduled(fixedDelay = 1000)
    public void queueScheduler() {
        waitingQueueService.allowUsers(ENTER_COUNT);
    }
}
