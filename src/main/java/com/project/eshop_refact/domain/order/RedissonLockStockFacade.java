package com.project.eshop_refact.domain.order;


import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 분산 락(Redisson) 기반 재고 동시성 제어 파사드
 * 트랜잭션 진입 전 락을 제어하여 DB 커넥션 점유 시간을 최소화하고, Pub/Sub 기반 구현체로 Redis 부하를 완화합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockStockFacade {

    private final RedissonClient redissonClient;
    private final OrderService orderService; // 주문 서비스를 주입받음
    private final WaitingQueueService waitingQueueService; // [추가] 주입 필요

    // 락 획득 최대 대기 시간 (Fail-Fast 유도)
    @Value("${app.order.lock.wait-time:10}")
    private long waitTIme;
    // 데드락 방지를 위해 비즈니스 로직 수행 시간을 고려한 최대 락 점유 시간
    @Value("${app.order.lock.lease-time:3}")
    private long leaseTime; //비즈니스 로직 시간을 고려해 3초로 넉넉히 설정

    public Long order(Long userId, Long productId, int count) {
        RLock lock = redissonClient.getLock("product:stock:" + productId);
        Boolean lockAcquired = false;

        try {
            lockAcquired = lock.tryLock(waitTIme, leaseTime, TimeUnit.SECONDS);

            if (!lockAcquired) {
                log.warn("Redisson Lock 획득 실패 - ProductId: {}", productId);
                // 락 획득 실패 시 대기열(Active 상태)을 유지하여, 클라이언트가 순번을 잃지 않고 즉시 재요청할 수 있도록 처리합니다.
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            return orderService.order(userId, productId, count);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 에러가 발생했습니다.");
        } finally {
            try{
                if(lockAcquired && lock.isHeldByCurrentThread()){
                    lock.unlock();
                }
            }finally {
                // 락을 획득했던 사용자(주문 처리 완료 또는 비즈니스 예외 발생)만 대기열에서 제거하여 큐의 무결성을 유지합니다.
                if (lockAcquired) {
                    waitingQueueService.removeUser(userId);
                }
            }
        }
    }

    /**
     * 주문 취소 시 재고 복구 동시성 제어
     */
    public void cancelOrder(Long orderId, Long userId) {
        // 주문 정보를 이용해 락 대상 상품 ID 동적 식별
        Long productId = orderService.getProductIdByOrderId(orderId);

        RLock lock = redissonClient.getLock("product:stock:" + productId);
        boolean lockAcquired = false;

        try {
            lockAcquired = lock.tryLock(waitTIme, leaseTime, TimeUnit.SECONDS);

            if (!lockAcquired) {
                log.warn("Redisson Lock 획득 실패 (취소) - ProductId: {}", productId);
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            orderService.cancelOrder(orderId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 에러가 발생했습니다.");
        } finally {
            if(lockAcquired && lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }

}
