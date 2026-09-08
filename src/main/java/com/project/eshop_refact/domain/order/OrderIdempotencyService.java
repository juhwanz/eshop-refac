package com.project.eshop_refact.domain.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.global.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderIdempotencyService {

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonLockStockFacade redissonLockStockFacade;
    private final MeterRegistry meterRegistry;
    private final OrderService orderService;

    public record CachedOrder(int version, String fingerprint, Long orderId) { }

    // 트랜잭션을 열지 않습니다. 중복 INSERT의 롤백이 끝난 뒤 새 DB 조회로 결과를 복구합니다.
    public OrderDto.CreateResponse processOrderWithIdempotency(String key, Long userId, Long productId, int count) {
        OrderRequestIdentity identity = OrderRequestIdentity.of(key, productId, count);
        String redisKey = "idempotency:order:" + userId + ":" + key;
        CachedOrder cached = readCache(redisKey);
        if (cached != null) {
            identity.verifyFingerprint(cached.fingerprint());
            return new OrderDto.CreateResponse(cached.orderId());
        }
        Optional<Long> completed = orderService.findCompletedOrder(userId, identity);
        if (completed.isPresent()) {
            return complete(redisKey, identity, completed.get());
        }

        String token = "PROCESSING:" + UUID.randomUUID();
        boolean owned = claim(redisKey, token);
        try {
            Long orderId;
            try {
                orderId = redissonLockStockFacade.order(userId, productId, count, identity);
            } catch (DataIntegrityViolationException exception) {
                if (!isIdempotencyConstraint(exception)) {
                    throw exception;
                }
                orderId = orderService.findCompletedOrder(userId, identity).orElseThrow(() -> exception);
            }
            meterRegistry.counter("order.success.count").increment();
            return complete(redisKey, identity, orderId);
        } catch (RuntimeException exception) {
            meterRegistry.counter("order.fail.count", "reason",
                    exception instanceof BusinessException ? "business_error" : "system_error").increment();
            throw exception;
        } finally {
            if (owned) {
                release(redisKey, token);
            }
        }
    }

    private boolean isIdempotencyConstraint(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && name.replace("`", "").replace("'", "")
                        .endsWith("uk_orders_user_idempotency");
            }
        }
        return false;
    }

    private CachedOrder readCache(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.startsWith("PROCESSING")) {
                return null;
            }
            CachedOrder cached = objectMapper.readValue(value, CachedOrder.class);
            return cached.version() == 1 && cached.fingerprint() != null && cached.orderId() != null ? cached : null;
        } catch (Exception exception) {
            log.warn("주문 멱등성 캐시 읽기 실패; DB에서 확인합니다.");
            return null;
        }
    }

    private boolean claim(String key, String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, 3, TimeUnit.MINUTES));
        } catch (RuntimeException exception) {
            log.warn("주문 멱등성 캐시 선점 실패; DB 유일 제약으로 보호합니다.");
            return false;
        }
    }

    private OrderDto.CreateResponse complete(String key, OrderRequestIdentity identity, Long orderId) {
        try {
            String value = objectMapper.writeValueAsString(new CachedOrder(1, identity.fingerprint(), orderId));
            redisTemplate.opsForValue().set(key, value, 24, TimeUnit.HOURS);
        } catch (Exception exception) {
            log.warn("주문 완료 캐시 저장 실패 - OrderId: {}", orderId);
        }
        return new OrderDto.CreateResponse(orderId);
    }

    private void release(String key, String token) {
        try {
            redisTemplate.execute(RELEASE, List.of(key), token);
        } catch (RuntimeException exception) {
            log.warn("주문 처리 표시 정리 실패; TTL로 만료됩니다.");
        }
    }
}
