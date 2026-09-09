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
import java.util.Optional;

/**
 * 분산 락(Redisson) 기반 재고 동시성 제어 파사드입니다.
 * 상품 락을 획득한 뒤 트랜잭션 서비스를 호출하여 락 대기 중에는 DB 커넥션을 점유하지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockStockFacade {

    private final RedissonClient redissonClient;
    private final OrderService orderService;
    private final WaitingQueueService waitingQueueService;

    // 락 획득 최대 대기 시간 (Fail-Fast 유도)
    @Value("${app.order.lock.wait-time:10}")
    private long waitTime;

    public Long order(Long userId, Long productId, int count) {
        return order(userId, productId, count, null);
    }

    public Long order(Long userId, Long productId, int count, OrderRequestIdentity identity) {
        RLock lock = redissonClient.getLock("product:stock:" + productId);
        boolean lockAcquired = false;
        boolean admitted = false;

        try {
            // lease를 지정하지 않아 트랜잭션 종료까지 watchdog이 락을 갱신합니다.
            lockAcquired = lock.tryLock(waitTime, TimeUnit.SECONDS);

            if (!lockAcquired) {
                log.warn("Redisson Lock 획득 실패 - ProductId: {}", productId);
                // 락 획득 실패 시 대기열(Active 상태)을 유지하여, 클라이언트가 순번을 잃지 않고 즉시 재요청할 수 있도록 처리합니다.
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            if (identity != null) {
                Optional<Long> completed = orderService.findCompletedOrder(userId, identity);
                if (completed.isPresent()) {
                    return completed.get();
                }
                if (!waitingQueueService.isAllowed(userId, productId)) {
                    throw new BusinessException(ErrorCode.QUEUE_WAITING);
                }
                admitted = true;
                return orderService.order(userId, productId, count, identity);
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
            } catch (RuntimeException cleanupFailure) {
                log.warn("주문 락 정리 실패 - ProductId: {}", productId, cleanupFailure);
            } finally {
                // 해당 상품의 활성 권한을 확인하고 주문을 시도한 경우에만 권한을 소비합니다.
                if (admitted) {
                    try {
                        waitingQueueService.removeUser(userId, productId);
                    } catch (RuntimeException cleanupFailure) {
                        log.warn("주문 대기열 정리 실패 - UserId: {}, ProductId: {}", userId, productId, cleanupFailure);
                    }
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
            lockAcquired = lock.tryLock(waitTime, TimeUnit.SECONDS);

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
