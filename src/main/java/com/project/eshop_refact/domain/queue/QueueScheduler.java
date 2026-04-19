package com.project.eshop_refact.domain.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 상태 갱신 스케줄러
 * 다중 인스턴스 환경에서 스케줄러의 중복 실행을 제어하기 위해 ShedLock을 사용합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final WaitingQueueService waitingQueueService;

    // TODO: 향후 부하 테스트를 통해 시스템 병목 지점을 파악하고 적정 허용량을 산출해야 합니다.
    private static final long ENTER_COUNT = 100L;

    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(
            name = "queueSchedulerLock",    // 락의 고유 이름
            lockAtLeastFor = "900ms",       // 락을 유지할 최소 시간 (중복 실행 완벽 방지)
            lockAtMostFor = "2s"            // 락을 유지할 최대 시간 (서버 다운 시 데드락 방지)
    )
    public void queueScheduler() {
        log.info("Queue Scheduler 실행: 락 획득 성공");
        waitingQueueService.allowUsers(ENTER_COUNT);
    }
}