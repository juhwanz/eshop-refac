package com.project.eshop_refact.facade;


import com.project.eshop_refact.service.OrderService;
import com.project.eshop_refact.service.queue.WaitingQueueService;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockStockFacade {

    private final RedissonClient redissonClient;
    private final OrderService orderService; // 주문 서비스를 주입받음
    private final WaitingQueueService waitingQueueService; // [추가] 주입 필요

    /**
     * Redisson Distributed Lock Facade
     * <p>
     * 트랜잭션 범위 밖에서 락을 제어하여 DB 커넥션 점유 시간을 최소화함.
     * Pub/Sub 기반의 락 구현체로 Redis 부하를 줄임 (vs Spin Lock).
     * </p>
     */

    // 락 획득 대기 시간 (Wait Time): 10초
    // (이유: 트래픽 폭주 시 대기열에서 너무 오래 기다리지 않고 Fail-Fast 처리하기 위함)
    @Value("${app.order.lock.wait-time:10}")
    private long waitTIme;
    // 락 대기 시간 및 점유 시간 설정
    // (이유: 로직이 멈춰도 1초 뒤 강제 해제하여 데드락 방지)
    @Value("${app.order.lock.lease-time:3}")
    private long leaseTime; // [Tuning] 비즈니스 로직 시간을 고려해 3초로 넉넉히 설정

    /**
     * [Architecture Note] Facade 패턴을 적용한 이유
     * 1. 관심사 분리: 비즈니스 로직(OrderService)과 인프라 로직(Lock, Queue)을 분리함.
     * 2. 트랜잭션 범위 최소화:
     * - @Transactional이 시작되기 전에 Lock을 잡고, 커밋이 완료된 후에 Lock을 해제해야 함.
     * - AOP 방식(@Transactional) 내부에 락을 걸면, 커밋 전에 락이 풀려 동시성 정합성이 깨질 수 있음.
     * - 따라서, Facade에서 락을 제어하고 Service에서 트랜잭션을 수행하는 계층 구조를 채택함.
     */
    public Long order(Long userId, Long productId, int quantity) {
        // Lock Key: 상품 단위로 락을 걸어 동시성 제어
        RLock lock = redissonClient.getLock("product:stock:" + productId);

        try {
            // 락 획득 시도 (최대 10초 대기, 락 획득 후 1초 지나면 자동 해제)
            boolean available = lock.tryLock(waitTIme, leaseTime, TimeUnit.SECONDS);

            if (!available) {
                log.warn("Redisson Lock 획득 실패 - ProductId: {}", productId);
                // removeUser 미호출 -> Active 상태 유지. => 이 메시지 보고, 재요청 시 바로 진입 가능.
                throw new IllegalStateException("현재 주문량이 많아 처리가 지연되고 있습니다.");
            }

            // 락 획득 성공 시 비즈니스 로직 수행
            // 트랜잭션은 이 메서드(orderService.order) 내부에서 시작되고 끝납니다.
            return orderService.order(userId, productId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 스레드 인터럽트 상태 복구
            throw new IllegalStateException("서버 에러가 발생했습니다.");
        } finally {
            // 3. 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                // 정상적으로 로직 수행 했거나, 수행 중 에러가 났을 떄만 퇴장.
                waitingQueueService.removeUser(userId);
            }
        }
    }
}
