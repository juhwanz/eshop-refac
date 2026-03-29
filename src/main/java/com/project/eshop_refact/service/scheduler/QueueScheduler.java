package com.project.eshop_refact.service.scheduler;

import com.project.eshop_refact.service.queue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final WaitingQueueService waitingQueueService;

    private static final long ENTER_COUNT = 100L; // TODO: 향후 부하 테스트를 통해 적정 허용량 산출 필요 (현재는 임시값 100으로 설정)

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