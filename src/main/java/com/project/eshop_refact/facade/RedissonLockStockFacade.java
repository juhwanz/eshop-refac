package com.project.eshop_refact.facade;


import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import com.project.eshop_refact.service.OrderService;
import com.project.eshop_refact.service.queue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
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
     * 트랜잭션 범위 밖에서 락을 제어하여 DB 커넥션 점유 시간을 최소화함.
     * Pub/Sub 기반의 락 구현체로 Redis 부하를 줄임 (vs Spin Lock).
     */

    // 락 획득 대기 시간 (Wait Time): 10초 -> 트래픽 폭주 시 대기열에서 너무 오래 기다리지 않고 Fail-Fast
    @Value("${app.order.lock.wait-time:10}")
    private long waitTIme;
    // 락 대기 시간 및 점유 시간 설정 -> 로직이 멈춰도 1초 뒤 강제 해제하여 데드락 방지)
    @Value("${app.order.lock.lease-time:3}")
    private long leaseTime; //비즈니스 로직 시간을 고려해 3초로 넉넉히 설정

    public Long order(Long userId, Long productId, int count) {
        // Lock Key: 상품 단위로 락을 걸어 동시성 제어
        RLock lock = redissonClient.getLock("product:stock:" + productId);

        try {
            // 락 획득 시도 (최대 10초 대기, 락 획득 후 1초 지나면 자동 해제)
            boolean available = lock.tryLock(waitTIme, leaseTime, TimeUnit.SECONDS);

            if (!available) {
                log.warn("Redisson Lock 획득 실패 - ProductId: {}", productId);
                // removeUser 미호출 -> Active 상태 유지. => 이 메시지 보고, 재요청 시 바로 진입 가능.
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            // 락 획득 성공 시 비즈니스 로직 수행
            // 트랜잭션은 이 메서드(orderService.order) 내부에서 시작되고 끝납니다.
            return orderService.order(userId, productId, count);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 스레드 인터럽트 상태 복구
            throw new IllegalStateException("서버 에러가 발생했습니다.");
        } finally {
            // 3. 락 해제
            // 락 해제 시도와 관계 없이 대기열 퇴장은 별도 보장
            try{
                if(lock.isHeldByCurrentThread()){
                    lock.unlock();
                }
            }finally {
                // 해제 중 에러나도 대기열에서 반드시 제거
                waitingQueueService.removeUser(userId);
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

        try {
            boolean available = lock.tryLock(waitTIme, leaseTime, TimeUnit.SECONDS);

            if (!available) {
                log.warn("Redisson Lock 획득 실패 (취소) - ProductId: {}", productId);
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            orderService.cancelOrder(orderId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 에러가 발생했습니다.");
        } finally {
            if(lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }

}
