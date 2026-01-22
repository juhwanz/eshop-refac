package com.project.eshop_refact.facade;


import com.project.eshop_refact.service.OrderService;
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

    /**
     * Redisson Distributed Lock Facade
     * <p>
     * 트랜잭션 범위 밖에서 락을 제어하여 DB 커넥션 점유 시간을 최소화함.
     * Pub/Sub 기반의 락 구현체로 Redis 부하를 줄임 (vs Spin Lock).
     * </p>
     */
    public Long order(Long userId, Long productId, int quantity) {
        // Lock Key: 상품 단위로 락을 걸어 동시성 제어
        RLock lock = redissonClient.getLock("product:stock:" + productId);

        try {
            // 락 획득 시도 (최대 10초 대기, 락 획득 후 1초 지나면 자동 해제)
            boolean available = lock.tryLock(10, 1, TimeUnit.SECONDS);

            if (!available) {
                log.warn("Redisson Lock 획득 실패 - ProductId: {}", productId);
                throw new IllegalStateException("현재 주문량이 많아 처리가 지연되고 있습니다.");
            }

            // 락 획득 성공 시 비즈니스 로직 수행
            return orderService.order(userId, productId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 스레드 인터럽트 상태 복구
            throw new IllegalStateException("서버 에러가 발생했습니다.");
        } finally {
            // 3. 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
