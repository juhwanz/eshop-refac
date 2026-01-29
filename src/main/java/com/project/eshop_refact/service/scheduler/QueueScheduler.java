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

    private static final long ENTER_COUNT = 10L;

    @Scheduled(fixedDelay = 1000)
    public void queueScheduler() {
        waitingQueueService.allowUsers(ENTER_COUNT);
    }
}
