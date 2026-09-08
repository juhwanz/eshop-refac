package com.project.eshop_refact.domain.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.queue.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class QueueScheduler {

    private final WaitingQueueService waitingQueueService;

    @Scheduled(
            fixedDelayString = "${app.queue.schedule-delay-ms:1000}",
            initialDelayString = "${app.queue.initial-delay-ms:1000}",
            timeUnit = TimeUnit.MILLISECONDS
    )
    @SchedulerLock(
            name = "productQueuePromotion",
            lockAtLeastFor = "${app.queue.scheduler-lock-at-least:900ms}",
            lockAtMostFor = "${app.queue.scheduler-lock-at-most:30s}"
    )
    public void promote() {
        long promoted = waitingQueueService.allowWaitingProducts();
        if (promoted > 0) {
            log.info("상품 대기열 사용자 {}명을 활성화했습니다.", promoted);
        }
    }
}
