package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.queue.QueueScheduler;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "app.queue.scheduler-enabled=true",
        "app.queue.initial-delay-ms=3600000",
        "app.queue.scheduler-lock-at-least=0s",
        "app.queue.scheduler-lock-at-most=10s"
})
class QueueSchedulerLockIntegrationTest extends MariaDbRedisIntegrationTest {

    @Autowired QueueScheduler scheduler;
    @SpyBean WaitingQueueService queue;

    @Test
    void concurrentSchedulerInvocationsDoNotPromoteTwice() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        doAnswer(invocation -> {
            executions.incrementAndGet();
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return 0L;
        }).when(queue).allowWaitingProducts();

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(scheduler::promote);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(scheduler::promote);
            second.get(5, TimeUnit.SECONDS);
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(executions).hasValue(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
